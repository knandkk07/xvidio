package com.xvideo.app.util;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class DeviceInfoUtil {
    private static final String TAG = "DeviceInfoUtil";
    private final Context context;
    private final SharedPrefManager prefs;

    public interface PublicIPListener {
        void onPublicIPFetched(String ip);
    }

    public DeviceInfoUtil(Context context) {
        this.context = context;
        this.prefs = new SharedPrefManager(context);
    }

    public static String getDeviceID(Context context) {
        return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    public static String getModel() {
        return Build.MODEL;
    }

    public static String getManufacturer() {
        return Build.MANUFACTURER;
    }

    public static String getAndroidVersion() {
        return Build.VERSION.RELEASE;
    }

    public static int getSDKVersion() {
        return Build.VERSION.SDK_INT;
    }

    public static String getCPUArchitecture() {
        return Build.SUPPORTED_ABIS[0];
    }

    public String getServiceProvider() {
        try {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                return tm.getNetworkOperatorName();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting service provider", e);
        }
        return "Unknown";
    }

    public static String getSelfNumber(Context context) {
        try {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (context.checkSelfPermission("android.permission.READ_PHONE_STATE") == 0 ||
                context.checkSelfPermission("android.permission.READ_PHONE_NUMBERS") == 0) {
                return tm.getLine1Number();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting phone number", e);
        }
        return "Permission denied";
    }

    public boolean isRooted() {
        String[] paths = {
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        };
        for (String path : paths) {
            if (new File(path).exists()) {
                return true;
            }
        }
        return false;
    }

    public boolean isSDCardAvailable() {
        return Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
    }

    public String getStorageMemory() {
        try {
            File path = Environment.getDataDirectory();
            long totalSpace = path.getTotalSpace();
            return formatSize(totalSpace);
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private String formatSize(long bytes) {
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        double size = bytes;
        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }
        return String.format("%.2f %s", size, units[unitIndex]);
    }

    public void getPublicIP(PublicIPListener listener) {
        new FetchPublicIPTask(listener).execute();
    }

    private static class FetchPublicIPTask extends AsyncTask<Void, Void, String> {
        private final PublicIPListener listener;

        FetchPublicIPTask(PublicIPListener listener) {
            this.listener = listener;
        }

        @Override
        protected String doInBackground(Void... voids) {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL("https://api.ipify.org").openConnection();
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String ip = reader.readLine();
                reader.close();
                conn.disconnect();
                return ip;
            } catch (Exception e) {
                return "Unknown";
            }
        }

        @Override
        protected void onPostExecute(String result) {
            if (listener != null) {
                listener.onPublicIPFetched(result);
            }
        }
    }

    public ArrayList<HashMap<String, String>> getSimInfo() {
        ArrayList<HashMap<String, String>> simList = new ArrayList<>();
        try {
            SubscriptionManager sm = SubscriptionManager.from(context);
            List<SubscriptionInfo> subs = sm.getActiveSubscriptionInfoList();
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

            if (subs != null && !subs.isEmpty()) {
                for (SubscriptionInfo info : subs) {
                    HashMap<String, String> sim = new HashMap<>();
                    String number = info.getNumber();
                    if (number == null || number.isEmpty()) {
                        number = tm.getLine1Number();
                    }
                    sim.put("simNumber", number != null ? number : "Unknown");
                    sim.put("carrierName", info.getCarrierName() != null ? info.getCarrierName().toString() : "Unknown");
                    sim.put("simSlotIndex", String.valueOf(info.getSimSlotIndex()));
                    simList.add(sim);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting SIM info", e);
        }
        return simList;
    }
}
