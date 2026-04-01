package com.parentalcontrol.childapp.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.firebase.database.ValueEventListener;
import com.parentalcontrol.childapp.R;
import com.parentalcontrol.childapp.activities.MainActivity;
import com.parentalcontrol.childapp.utils.FirebaseHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * MonitorForegroundService
 * ────────────────────────
 * A foreground service that keeps keyword monitoring alive even when the
 * app is in the background or the screen is off.
 *
 * Responsibilities:
 *   - Show a persistent notification so Android doesn't kill the process.
 *   - Keep a live Firebase keyword listener active.
 *   - Expose the current keyword list to SmsReceiver and other components
 *     via a static accessor, so they don't have to re-fetch from Firebase
 *     every time.
 *
 * Started from:
 *   - MainActivity.onCreate()   → when the app opens
 *   - BootReceiver.onReceive()  → after device reboot
 */
public class MonitorForegroundService extends Service {

    private static final String TAG            = "MonitorFgService";
    private static final String CHANNEL_ID     = "parental_monitor_channel";
    private static final int    NOTIF_ID       = 1001;

    // Shared keyword list – updated live from Firebase
    private static final List<String> cachedKeywords = new ArrayList<>();
    private ValueEventListener keywordListener;

    // ── Static accessor ───────────────────────────────────────────────────────

    /**
     * Returns a copy of the current cached keyword list.
     * Other classes (e.g. SmsReceiver) can use this instead of hitting Firebase.
     */
    public static List<String> getCachedKeywords() {
        synchronized (cachedKeywords) {
            return new ArrayList<>(cachedKeywords);
        }
    }

    // ── Service lifecycle ────────────────────────────────────────────────────

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "MonitorForegroundService starting...");

        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());

        // Keep keywords in sync with Firebase in real time
        keywordListener = FirebaseHelper.getInstance()
                .listenForKeywords(new FirebaseHelper.KeywordsCallback() {
                    @Override
                    public void onKeywordsLoaded(List<String> updatedKeywords) {
                        synchronized (cachedKeywords) {
                            cachedKeywords.clear();
                            cachedKeywords.addAll(updatedKeywords);
                        }
                        Log.d(TAG, "Cached keywords updated: " + cachedKeywords);
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "Keyword sync error: " + error);
                    }
                });

        // START_STICKY: restart service automatically if killed by OS
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        FirebaseHelper.getInstance().removeKeywordListener(keywordListener);
        Log.d(TAG, "MonitorForegroundService stopped.");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not a bound service
    }

    // ── Notification helpers ─────────────────────────────────────────────────

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Parental Monitor",
                    NotificationManager.IMPORTANCE_LOW  // Silent – no sound
            );
            channel.setDescription("Keeps parental monitoring active in the background");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        // Tapping the notification opens MainActivity
        Intent notifIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notifIntent,
                PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Parental Monitor Active")
                .setContentText("Monitoring messages for restricted content.")
                .setSmallIcon(R.drawable.ic_shield)   // add ic_shield.xml to drawable
                .setContentIntent(pendingIntent)
                .setOngoing(true)   // User cannot dismiss this notification
                .build();
    }
}
