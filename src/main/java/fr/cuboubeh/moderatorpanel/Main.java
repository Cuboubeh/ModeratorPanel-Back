package fr.cuboubeh.moderatorpanel;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import fr.cuboubeh.moderatorpanel.utils.AccountChecker;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.util.Random;

public class Main extends JavaPlugin implements Listener {
    private FileConfiguration config;
    private Connection connection;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();

        String dbUrl = config.getString("db.url");
        String dbUser = config.getString("db.user");
        String dbPassword = config.getString("db.password");

        try {
            connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
            System.out.println("[ModeratorPanel] - Connection à la base de données réussie");
        } catch (SQLException e) {
            e.printStackTrace();
        }

        getServer().getPluginManager().registerEvents((Listener) this, this);
        getServer().getPluginManager().registerEvents((Listener) this, this);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        try {
            if(connection != null) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event){
        String playerName = event.getPlayer().getName();
        Player player = event.getPlayer();
        System.out.println("[ModeratorPanel] - " + playerName + " vient de se connecter au serveur.");

        if(!isPlayerRegistered(playerName)) {
            boolean isCracked = AccountChecker.isCracked(playerName);

            registerPlayer(playerName, isCracked);
        }

        String discordId = getDiscordId(playerName);
        if(discordId.equals("0") || discordId.isEmpty()) {
            kickPlayerWithoutDiscord(playerName);
            System.out.println("[ModeratorPanel] - " + playerName + " n'est pas enregistré sur le serveur.");
            return;
        } else {
            Town town = TownyAPI.getInstance().getTown(player);

            String townName = "Aucune ville";
            if(town != null) {
                townName = town.getName();
            }

            updateLastLoginAndStatus(playerName, true, townName);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event){
        Player player = event.getPlayer();
        String playerName = player.getName();
        Block block = event.getBlock();

        if(player.getGameMode() != GameMode.SURVIVAL){
            return;
        }

        Material blockType = event.getBlock().getType();
        if(!wasBlockPlacedByPlayer(block)) {
            incrementOresMines(blockType, playerName);
        }

    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        // Ajouter des métadonnées au bloc indiquant qu'il a été placé par un joueur
        block.setMetadata("placedByPlayer", new FixedMetadataValue(this, true));

        // Autres actions à effectuer lors de la pose du bloc par un joueur...
    }

    private boolean wasBlockPlacedByPlayer(Block block) {
        if (block.hasMetadata("placedByPlayer")) {
            return true;
        }
        return false;
    }

    private void incrementOresMines(Material blockType, String playerName) {
        String columnName = getColumnNameForOre(blockType); // Obtenez le nom de la colonne pour le type de minerai
        if (columnName == null) {
            return; // Ne rien faire si le type de minerai n'est pas suivi
        }

        String query = "UPDATE players SET " + columnName + " = " + columnName + " + 1 WHERE player_name = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerName);
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private String getColumnNameForOre(Material oreType) {
        // Mappez les types de minerai aux noms de colonnes correspondants dans votre table
        switch (oreType) {
            case ANCIENT_DEBRIS:
            case NETHERITE_BLOCK:
                return "ancien_debrits_mined";
            case DIAMOND_ORE:
            case DIAMOND_BLOCK:
            case DEEPSLATE_DIAMOND_ORE:
                return "diamond_ore_mined";
            case COPPER_ORE:
            case COPPER_BLOCK:
            case DEEPSLATE_COPPER_ORE:
                return "copper_ore_mined";
            case IRON_ORE:
            case IRON_BLOCK:
            case DEEPSLATE_IRON_ORE:
                return "iron_ore_mined";
            case LAPIS_ORE:
            case LAPIS_BLOCK:
            case DEEPSLATE_LAPIS_ORE:
                return "lapis_ore_mined";
            case COAL_ORE:
            case COAL_BLOCK:
            case DEEPSLATE_COAL_ORE:
                return "coal_ore_mined";
            case REDSTONE_ORE:
            case DEEPSLATE_REDSTONE_ORE:
            case REDSTONE_BLOCK:
                return "redstone_ore_mined";
            case EMERALD_ORE:
            case DEEPSLATE_EMERALD_ORE:
            case EMERALD_BLOCK:
                return "emerald_ore_mined";
            case GOLD_ORE:
            case GOLD_BLOCK:
            case DEEPSLATE_GOLD_ORE:
                return "gold_ore_mined";
            case NETHER_GOLD_ORE:
                return "nether_gold_ore_mined";
            case NETHER_QUARTZ_ORE:
            case QUARTZ_BLOCK:
                return "quartz_ore_mined";
            case STONE:
            case GRAVEL:
            case GRANITE:
            case ANDESITE:
            case DIORITE:
            case SANDSTONE:
            case PACKED_MUD:
            case GRASS_BLOCK:
            case DIRT:
            case SAND:
            case DEEPSLATE:
                return "stones";
            case NETHERRACK:
                return "netherrack";
            default:
                return null; // Si le type de minerai n'est pas suivi
        }
    }


    private String getDiscordId(String playerName) {
        String query = "SELECT discord_id FROM players WHERE player_name = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getString("discord_id");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void kickPlayerWithoutDiscord(String playerName) {
        String kickMessage = "Vous devez lier votre compte Discord pour jouer. \n";
        int randomCode = generateRandomCode();
        saveDiscordCode(playerName, randomCode);
        kickMessage += " Code de vérification à saisir sur Discord: §6" + randomCode;

        Player player = Bukkit.getPlayer(playerName);
        if(player != null) {
            player.kickPlayer(kickMessage);
        }
    }

    private int generateRandomCode() {
        Random random = new Random();
        return random.nextInt(9000) + 1000;
    }

    private void saveDiscordCode(String playerName, int code) {
        String query = "UPDATE players SET discord_code = ? WHERE player_name = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, code);
            statement.setString(2, playerName);
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        String playerName = event.getPlayer().getName();
        Player player = event.getPlayer();
        System.out.println("[ModeratorPanel] - " + playerName + " vient de quitter le serveur.");

        Town town = TownyAPI.getInstance().getTown(player);

        String townName = "Aucune ville";
        if(town != null) {
            townName = town.getName();
        }

        updateLastLoginAndStatus(playerName, true, townName);
    }

    private boolean isPlayerRegistered(String playerName) {
        String query = "SELECT id FROM players WHERE player_name = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private void registerPlayer(String playerName, boolean isCracked) {
        String query = "INSERT INTO players (player_name, cracked_account) VALUES (?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, playerName);
            statement.setBoolean(2, isCracked);
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void updateLastLoginAndStatus(String playerName, boolean isOnline, String town) {
        String query = "UPDATE players SET last_login = ?, is_online = ?, town_name = ? WHERE player_name = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
            statement.setBoolean(2, isOnline);
            statement.setString(3, town);
            statement.setString(4, playerName);
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

}