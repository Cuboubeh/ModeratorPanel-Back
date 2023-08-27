package fr.cuboubeh.moderatorpanel.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

public class AccountChecker {

    public static boolean isCracked(String playerName) {
        try {
            UUID uuid = Bukkit.getOfflinePlayer(playerName).getUniqueId();
            URL url = new URL("https://api.mojang.com/user/profiles/" + uuid.toString().replace("-", "") + "/names");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("GET");
            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String response = in.readLine();
                in.close();

                JsonParser parser = new JsonParser();
                JsonObject json = parser.parse(response).getAsJsonObject();

                // If the response has a name field, then the account is premium
                return !json.has("name"); // If name field is absent, account is cracked
            } else {
                return false; // Error or cracked account
            }
        } catch (Exception e) {
            e.printStackTrace();
            return true; // Error or cracked account
        }
    }
}
