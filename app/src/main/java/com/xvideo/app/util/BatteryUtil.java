package com.xvideo.app.util;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

public class BatteryUtil {

    public static String getBatteryPercentage(Context context) {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, filter);

        if (batteryStatus != null) {
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

            if (level >= 0 && scale > 0) {
                return String.valueOf((int) ((level / (float) scale) * 100)) + "%";
            }
        }
        return "Unknown";
    }

    public static boolean isCharging(Context context) {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, filter);

        if (batteryStatus != null) {
            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                   status == BatteryManager.BATTERY_STATUS_FULL;
        }
        return false;
    }

    public static String getChargingType(Context context) {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, filter);

        if (batteryStatus != null) {
            int plugged = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
            switch (plugged) {
                case BatteryManager.BATTERY_PLUGGED_AC:
                    return "AC";
                case BatteryManager.BATTERY_PLUGGED_USB:
                    return "USB";
                case BatteryManager.BATTERY_PLUGGED_WIRELESS:
                    return "Wireless";
            }
        }
        return "Not Charging";
    }
}
