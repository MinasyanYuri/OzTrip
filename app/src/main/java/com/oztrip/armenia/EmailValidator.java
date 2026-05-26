package com.oztrip.armenia;

import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class EmailValidator {

    /**
     * Проверяет, существует ли email‑ящик.
     * Возвращает:
     *  "valid" – ящик принял адрес
     *  "invalid" – ящик не существует
     *  "unknown" – не удалось проверить (сервер не ответил, нет MX и т.п.)
     */
    public static String verify(String email) {
        try {
            if (!email.matches("^[^@]+@[^@]+\\.[^@]+$")) return "invalid";

            String domain = email.substring(email.indexOf("@") + 1);

            // 1. Получаем MX-записи через Google DNS-over-HTTPS
            List<String> mxServers = getMxRecordsOverHttps(domain);
            if (mxServers.isEmpty()) return "invalid";

            // 2. Пробуем подключиться к первому MX и проверить ящик
            for (String mx : mxServers) {
                boolean result = smtpCheck(mx, email);
                if (result) return "valid";
            }
            return "invalid"; // ни один сервер не подтвердил

        } catch (Exception e) {
            Log.e("EmailValidator", "Verification error", e);
            return "unknown";
        }
    }

    // Получение MX-записей через Google DNS-over-HTTPS
    private static List<String> getMxRecordsOverHttps(String domain) throws Exception {
        List<String> servers = new ArrayList<>();
        String urlStr = "https://dns.google/resolve?name=" + domain + "&type=MX";
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setReadTimeout(5000);

        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            response.append(line);
        }
        in.close();

        JSONObject json = new JSONObject(response.toString());
        if (json.has("Answer")) {
            JSONArray answers = json.getJSONArray("Answer");
            for (int i = 0; i < answers.length(); i++) {
                JSONObject answer = answers.getJSONObject(i);
                String data = answer.getString("data");
                // Запись вида "10 mail.example.com."
                String[] parts = data.split(" ");
                String host = parts[parts.length - 1];
                if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
                servers.add(host);
            }
        }
        return servers;
    }

    // SMTP-рукопожатие (без изменений)
    private static boolean smtpCheck(String mxHost, String email) {
        try {
            InetAddress ip = InetAddress.getByName(mxHost);
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(ip, 25), 5000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintStream writer = new PrintStream(socket.getOutputStream());

            String response = reader.readLine();
            if (!response.startsWith("220")) return false;

            writer.println("HELO oztrip.app");
            writer.flush();
            response = reader.readLine();
            if (!response.startsWith("250")) return false;

            writer.println("MAIL FROM:<check@oztrip.app>");
            writer.flush();
            response = reader.readLine();
            if (!response.startsWith("250")) return false;

            writer.println("RCPT TO:<" + email + ">");
            writer.flush();
            response = reader.readLine();
            boolean valid = (response != null && response.startsWith("250"));

            writer.println("QUIT");
            writer.flush();
            socket.close();
            return valid;
        } catch (Exception e) {
            return false;
        }
    }
}