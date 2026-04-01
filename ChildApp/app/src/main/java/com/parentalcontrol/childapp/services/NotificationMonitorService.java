package com.parentalcontrol.childapp.services;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.google.firebase.database.ValueEventListener;
import com.parentalcontrol.childapp.utils.FirebaseHelper;
import com.parentalcontrol.childapp.utils.KeywordChecker;

import java.util.ArrayList;
import java.util.List;

/**
 * NotificationMonitorService
 * ──────────────────────────
 * A NotificationListenerService that reads the text of all incoming
 * notifications (from WhatsApp, Instagram, Telegram, etc.) and checks
 * for restricted keywords.
 *
 * HOW TO ENABLE:
 *   The user must manually grant access:
 *   Settings → Apps & Notifications → Special App Access → Notification Access
 *   → Enable "Child Monitor"
 *
 * Monitored apps (configurable via MONITORED_PACKAGES below):
 *   - WhatsApp
 *   - Instagram
 *   - Telegram
 *   - Facebook Messenger
 *   - Snapchat
 *   - Default SMS app (com.google.android.apps.messaging / com.samsung.android.messaging)
 */
public class NotificationMonitorService extends NotificationListenerService {

    private static final String TAG = "NotifMonitorService";

    /** Add or remove package names to control which apps are monitored. */
    private static final String[] MONITORED_PACKAGES = {
            "com.whatsapp",                          // WhatsApp
            "com.whatsapp.w4b",                      // WhatsApp Business
            "com.instagram.android",                 // Instagram
            "org.telegram.messenger",                // Telegram
            "com.facebook.orca",                     // Facebook Messenger
            "com.snapchat.android",                  // Snapchat
            "com.google.android.apps.messaging",     // Google Messages (SMS)
            "com.samsung.android.messaging",         // Samsung Messages (SMS)
            "com.android.mms"                        // AOSP Messages
    };

    // Live keyword list kept in sync with Firebase
    private final List<String> keywords = new ArrayList<>();
    private ValueEventListener keywordListener;

    // ── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "NotificationMonitorService started.");

        // Start listening for keyword changes in real time
        keywordListener = FirebaseHelper.getInstance()
                .listenForKeywords(new FirebaseHelper.KeywordsCallback() {
                    @Override
                    public void onKeywordsLoaded(List<String> updatedKeywords) {
                        synchronized (keywords) {
                            keywords.clear();
                            keywords.addAll(updatedKeywords);
                        }
                        Log.d(TAG, "Keywords updated: " + keywords);
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "Keyword load error: " + error);
                    }
                });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Remove Firebase listener to avoid memory leaks
        FirebaseHelper.getInstance().removeKeywordListener(keywordListener);
        Log.d(TAG, "NotificationMonitorService destroyed.");
    }

    // ── Notification handling ─────────────────────────────────────────────────

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;

        String packageName = sbn.getPackageName();

        // Only process notifications from monitored apps
        if (!isMonitoredPackage(packageName)) return;

        Notification notification = sbn.getNotification();
        if (notification == null) return;

        Bundle extras = notification.extras;
        if (extras == null) return;

        // Extract notification title and text
        CharSequence titleCs = extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence textCs  = extras.getCharSequence(Notification.EXTRA_TEXT);
        CharSequence bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);

        String title  = titleCs  != null ? titleCs.toString()  : "";
        String body   = textCs   != null ? textCs.toString()   : "";
        String bigMsg = bigText  != null ? bigText.toString()  : "";

        // Prefer big text (full message) over the short notification preview
        String fullMessage = bigMsg.isEmpty() ? body : bigMsg;
        String sender      = title.isEmpty()  ? packageName : title;

        Log.d(TAG, "Notification from " + packageName + " | " + sender + ": " + fullMessage);

        // ── Keyword check ────────────────────────────────────────────────────
        List<String> currentKeywords;
        synchronized (keywords) {
            currentKeywords = new ArrayList<>(keywords);
        }

        String matchedKeyword = KeywordChecker.findMatch(fullMessage, currentKeywords);
        if (matchedKeyword == null) {
            // Also check title in case message body is empty (e.g. image captions)
            matchedKeyword = KeywordChecker.findMatch(sender + " " + body, currentKeywords);
        }

        if (matchedKeyword != null) {
            final String finalKeyword = matchedKeyword;
            final String appLabel     = getFriendlyAppName(packageName);

            Log.d(TAG, "ALERT! Keyword '" + finalKeyword + "' found in " + appLabel);

            FirebaseHelper.getInstance().sendAlert(
                    fullMessage.isEmpty() ? body : fullMessage,
                    sender,
                    finalKeyword,
                    "Notification",
                    appLabel,
                    new FirebaseHelper.AlertCallback() {
                        @Override
                        public void onSuccess() {
                            Log.d(TAG, "Alert pushed to Firebase.");
                        }

                        @Override
                        public void onFailure(String error) {
                            Log.e(TAG, "Alert push failed: " + error);
                        }
                    }
            );
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private boolean isMonitoredPackage(String packageName) {
        for (String pkg : MONITORED_PACKAGES) {
            if (pkg.equals(packageName)) return true;
        }
        return false;
    }

    /** Returns a human-readable app name for the Firebase alert record. */
    private String getFriendlyAppName(String packageName) {
        switch (packageName) {
            case "com.whatsapp":
            case "com.whatsapp.w4b":        return "WhatsApp";
            case "com.instagram.android":    return "Instagram";
            case "org.telegram.messenger":   return "Telegram";
            case "com.facebook.orca":        return "Messenger";
            case "com.snapchat.android":     return "Snapchat";
            case "com.google.android.apps.messaging":
            case "com.samsung.android.messaging":
            case "com.android.mms":          return "SMS";
            default:                         return packageName;
        }
    }
}
