package com.xvideo.app.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.lang.reflect.Method;

public class NetworkUtil {
    private static final String TAG = "NetworkUtil";

    public static boolean isNetworkConnected(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Network network = cm.getActiveNetwork();
                    NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                    return caps != null && (
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                    );
                } else {
                    NetworkInfo info = cm.getActiveNetworkInfo();
                    return info != null && info.isConnected();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking network", e);
        }
        return false;
    }

    public static boolean isWifiConnected(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                NetworkInfo wifi = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                return wifi != null && wifi.isConnected();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking WiFi", e);
        }
        return false;
    }

    public static boolean isMobileDataConnected(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                NetworkInfo mobile = cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
                return mobile != null && mobile.isConnected();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking mobile data", e);
        }
        return false;
    }

    public static void enableWifi(Context context) {
        try {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null && !wifiManager.isWifiEnabled()) {
                wifiManager.setWifiEnabled(true);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error enabling WiFi", e);
        }
    }

    public static void enableMobileData(Context context) {
        try {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                Method method = tm.getClass().getDeclaredMethod("setDataEnabled", boolean.class);
                method.setAccessible(true);
                method.invoke(tm, true);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error enabling mobile data", e);
        }
    }

    public static String getActiveNetworkType(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
                    if (caps != null) {
                        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "WiFi";
                        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "Mobile";
                        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "Ethernet";
                        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return "VPN";
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting network type", e);
        }
        return "Unknown";
    }

    public static boolean isVpnConnected(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
                return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking VPN", e);
        }
        return false;
    }
}
