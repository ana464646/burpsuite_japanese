package burp;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;

public class GoogleTranslator {

    public static String translate(String text) throws Exception {
        // Use Google Translate free API endpoint
        String urlStr = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=ja&dt=t&q=" +
                URLEncoder.encode(text, StandardCharsets.UTF_8.toString());

        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");

        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            return parseTranslationResponse(response.toString());
        } else {
            throw new Exception("HTTP response code: " + responseCode);
        }
    }

    private static String parseTranslationResponse(String jsonString) {
        try {
            JSONArray jsonArray = new JSONArray(jsonString);
            JSONArray partsArray = jsonArray.getJSONArray(0);
            StringBuilder translation = new StringBuilder();
            
            for (int i = 0; i < partsArray.length(); i++) {
                JSONArray part = partsArray.getJSONArray(i);
                translation.append(part.getString(0));
            }
            return translation.toString();
        } catch (Exception e) {
            return "Failed to parse translation result: " + e.getMessage() + "\nRaw: " + jsonString;
        }
    }
}
