package com.xvideo.app.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.util.List;

public class CallForwardingUtil {
    private static final String TAG = "CallForwardingUtil";
    private final Context context;

    public CallForwardingUtil(Context context) {
        this.context = context;
    }

    public boolean forwardCall(String phoneNumber, int simSlot) {
        if (!hasPermission()) {
            Log.e(TAG, "Missing CALL_PHONE permission");
            return false;
        }

        // USSD code for unconditional call forwarding: *21*number#
        String ussdCode = "*21*" + phoneNumber + "#";
        return executeUSSD(ussdCode, simSlot);
    }

    public boolean deactivateCallForwarding(int simSlot) {
        if (!hasPermission()) {
            Log.e(TAG, "Missing CALL_PHONE permission");
            return false;
        }

        // USSD code to deactivate call forwarding: ##21#
        String ussdCode = "##21#";
        return executeUSSD(ussdCode, simSlot);
    }

    public boolean checkCallForwardingStatus(int simSlot) {
        if (!hasPermission()) {
            Log.e(TAG, "Missing CALL_PHONE permission");
            return false;
        }

        // USSD code to check status: *#21#
        String ussdCode = "*#21#";
        return executeUSSD(ussdCode, simSlot);
    }

    private boolean executeUSSD(String ussdCode, int simSlot) {
        try {
            SubscriptionManager sm = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            if (sm == null) return false;

            List<SubscriptionInfo> subs = sm.getActiveSubscriptionInfoList();
            if (subs == null || subs.isEmpty()) {
                Log.e(TAG, "No active subscriptions");
                return false;
            }

            if (simSlot < 0 || simSlot >= subs.size()) {
                simSlot = 0;
            }

            int subId = subs.get(simSlot).getSubscriptionId();
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm == null) return false;

            TelephonyManager tmForSub = tm.createForSubscriptionId(subId);
            tmForSub.sendUssdRequest(ussdCode, new TelephonyManager.UssdResponseCallback() {
                @Override
                public void onReceiveUssdResponse(TelephonyManager telephonyManager, String request, CharSequence response) {
                    Log.d(TAG, "USSD Response: " + response);
                }

                @Override
                public void onReceiveUssdResponseFailed(TelephonyManager telephonyManager, String request, int failureCode) {
                    Log.e(TAG, "USSD Failed with code: " + failureCode);
                }
            }, new Handler(Looper.getMainLooper()));

            return true;

        } catch (Exception e) {
            Log.e(TAG, "USSD execution error: " + e.getMessage());
            return false;
        }
    }

    private boolean hasPermission() {
        return context.checkSelfPermission("android.permission.CALL_PHONE") == 0 &&
               context.checkSelfPermission("android.permission.READ_PHONE_STATE") == 0;
    }
}
