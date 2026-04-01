package com.parentalcontrol.childapp.activities;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.parentalcontrol.childapp.R;
import com.parentalcontrol.childapp.services.MonitorForegroundService;
import com.parentalcontrol.childapp.services.NotificationMonitorService;

/**
 * MainActivity
 * ────────────
 * The child app's home/dashboard screen.
 *
 * Responsibilities:
 *   1. Show monitoring status (SMS, Notifications, In-App Chat).
 *   2. Request required runtime permissions (SMS, Notifications).
 *   3. Prompt user to grant Notification Access in system settings.
 *   4. Start the MonitorForegroundService.
 *   5. Navigate to ChatActivity.
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_SMS_PERMISSIONS = 100;
    private static final int REQUEST_NOTIF_PERMISSION = 101;  // Android 13+

    private TextView tvChildName, tvSmsStatus, tvNotifStatus, tvChatStatus;
    private Button   btnOpenChat, btnGrantNotifAccess, btnLogout;

    private String childName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Retrieve child name from intent or shared prefs
        childName = getIntent().getStringExtra("child_name");
        if (TextUtils.isEmpty(childName)) {
            childName = getSharedPreferences("prefs", MODE_PRIVATE)
                    .getString("child_name", "Child");
        }

        initViews();
        setupListeners();

        // Start the foreground monitoring service
        startMonitorService();

        // Request SMS permissions
        requestSmsPermissions();

        // Android 13+: request POST_NOTIFICATIONS for foreground service notif
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotifPermission();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatusIndicators();
    }

    // ── View setup ───────────────────────────────────────────────────────────

    private void initViews() {
        tvChildName          = findViewById(R.id.tv_child_name);
        tvSmsStatus          = findViewById(R.id.tv_sms_status);
        tvNotifStatus        = findViewById(R.id.tv_notif_status);
        tvChatStatus         = findViewById(R.id.tv_chat_status);
        btnOpenChat          = findViewById(R.id.btn_open_chat);
        btnGrantNotifAccess  = findViewById(R.id.btn_grant_notif_access);
        btnLogout            = findViewById(R.id.btn_logout);

        tvChildName.setText("Hello, " + childName + "!");
    }

    private void setupListeners() {
        btnOpenChat.setOnClickListener(v ->
                startActivity(new Intent(this, ChatActivity.class))
        );

        btnGrantNotifAccess.setOnClickListener(v -> openNotificationAccessSettings());

        btnLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    // ── Status indicators ────────────────────────────────────────────────────

    private void updateStatusIndicators() {
        // SMS permission
        boolean hasSms = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
                == PackageManager.PERMISSION_GRANTED;
        tvSmsStatus.setText("SMS Monitoring: " + (hasSms ? "Active" : "No Permission"));
        tvSmsStatus.setTextColor(getColor(hasSms ? R.color.green : R.color.red));

        // Notification listener
        boolean hasNotif = isNotificationListenerEnabled();
        tvNotifStatus.setText("App Notifications: " + (hasNotif ? "Active" : "Access Needed"));
        tvNotifStatus.setTextColor(getColor(hasNotif ? R.color.green : R.color.red));
        btnGrantNotifAccess.setVisibility(hasNotif ? android.view.View.GONE : android.view.View.VISIBLE);

        // In-app chat – always active
        tvChatStatus.setText("In-App Chat: Active");
        tvChatStatus.setTextColor(getColor(R.color.green));
    }

    // ── Service startup ──────────────────────────────────────────────────────

    private void startMonitorService() {
        Intent serviceIntent = new Intent(this, MonitorForegroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    // ── Permission handling ──────────────────────────────────────────────────

    private void requestSmsPermissions() {
        String[] permissions = {
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS
        };

        boolean allGranted = true;
        for (String perm : permissions) {
            if (ContextCompat.checkSelfPermission(this, perm)
                    != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (!allGranted) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_SMS_PERMISSIONS);
        }
    }

    private void requestNotifPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_NOTIF_PERMISSION
                );
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_SMS_PERMISSIONS) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (!granted) {
                Toast.makeText(this,
                        "SMS permission denied. SMS monitoring inactive.",
                        Toast.LENGTH_LONG).show();
            }
        }
        updateStatusIndicators();
    }

    // ── Notification Access ──────────────────────────────────────────────────

    private boolean isNotificationListenerEnabled() {
        String enabledListeners = Settings.Secure.getString(
                getContentResolver(),
                "enabled_notification_listeners"
        );
        if (TextUtils.isEmpty(enabledListeners)) return false;

        ComponentName cn = new ComponentName(this, NotificationMonitorService.class);
        return enabledListeners.contains(cn.flattenToString());
    }

    private void openNotificationAccessSettings() {
        new AlertDialog.Builder(this)
                .setTitle("Enable Notification Access")
                .setMessage("To monitor notifications from WhatsApp, Instagram, and other apps, "
                        + "please enable Notification Access for Child Monitor.\n\n"
                        + "Tap OK to open Settings, find 'Child Monitor' and enable it.")
                .setPositiveButton("Open Settings", (dialog, which) ->
                        startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                )
                .setNegativeButton("Cancel", null)
                .show();
    }
}
