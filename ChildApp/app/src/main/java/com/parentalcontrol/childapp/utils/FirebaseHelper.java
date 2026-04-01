package com.parentalcontrol.childapp.utils;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.parentalcontrol.childapp.models.Alert;

import java.util.ArrayList;
import java.util.List;

/**
 * FirebaseHelper
 * ──────────────
 * Central class for all Firebase Realtime Database operations.
 *
 * Database structure:
 *
 * root/
 * ├── keywords/          ← parent app writes restricted words here
 * │   ├── 0: "drugs"
 * │   ├── 1: "violence"
 * │   └── 2: "suicide"
 * └── alerts/            ← child app writes flagged messages here
 *     └── {push_id}/
 *         ├── message    : full message text
 *         ├── sender     : child's name / phone number
 *         ├── keyword    : which keyword was matched
 *         ├── source     : "SMS" | "Notification" | "InApp"
 *         ├── appName    : e.g. "WhatsApp", "Instagram", "ChildApp"
 *         └── timestamp  : epoch millis
 */
public class FirebaseHelper {

    // ── Singleton ────────────────────────────────────────────────────────────

    private static FirebaseHelper instance;

    public static FirebaseHelper getInstance() {
        if (instance == null) {
            instance = new FirebaseHelper();
        }
        return instance;
    }

    // ── Firebase references ──────────────────────────────────────────────────

    private final DatabaseReference rootRef;
    private final DatabaseReference keywordsRef;
    private final DatabaseReference alertsRef;

    private FirebaseHelper() {
        // FirebaseDatabase.getInstance() uses google-services.json automatically
        rootRef     = FirebaseDatabase.getInstance().getReference();
        keywordsRef = rootRef.child("keywords");
        alertsRef   = rootRef.child("alerts");
    }

    // ── Keyword loading ──────────────────────────────────────────────────────

    /**
     * Callback interface for keyword loading.
     */
    public interface KeywordsCallback {
        void onKeywordsLoaded(List<String> keywords);
        void onError(String error);
    }

    /**
     * Fetches the keyword list once from Firebase.
     * Call this before checking messages.
     */
    public void fetchKeywords(final KeywordsCallback callback) {
        keywordsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                List<String> keywords = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    String kw = child.getValue(String.class);
                    if (kw != null && !kw.trim().isEmpty()) {
                        keywords.add(kw.trim().toLowerCase());
                    }
                }
                callback.onKeywordsLoaded(keywords);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }

    /**
     * Keeps a live listener on keywords so the list stays updated in real-time.
     * Returns the listener so the caller can remove it when done.
     */
    public ValueEventListener listenForKeywords(final KeywordsCallback callback) {
        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                List<String> keywords = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    String kw = child.getValue(String.class);
                    if (kw != null && !kw.trim().isEmpty()) {
                        keywords.add(kw.trim().toLowerCase());
                    }
                }
                callback.onKeywordsLoaded(keywords);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                callback.onError(error.getMessage());
            }
        };
        keywordsRef.addValueEventListener(listener);
        return listener;
    }

    /** Removes a previously added keyword listener. */
    public void removeKeywordListener(ValueEventListener listener) {
        if (listener != null) {
            keywordsRef.removeEventListener(listener);
        }
    }

    // ── Alert sending ────────────────────────────────────────────────────────

    /**
     * Callback interface for alert sending.
     */
    public interface AlertCallback {
        void onSuccess();
        void onFailure(String error);
    }

    /**
     * Pushes a flagged-message alert to Firebase so the parent app is notified.
     *
     * @param message   Full text of the flagged message
     * @param sender    Who sent/received the message (child name or phone number)
     * @param keyword   The restricted keyword that was matched
     * @param source    "SMS" | "Notification" | "InApp"
     * @param appName   Package-friendly app label, e.g. "WhatsApp"
     * @param callback  Success/failure callback (can be null)
     */
    public void sendAlert(String message,
                          String sender,
                          String keyword,
                          String source,
                          String appName,
                          final AlertCallback callback) {

        Alert alert = new Alert(message, sender, keyword, source, appName,
                System.currentTimeMillis());

        alertsRef.push()
                .setValue(alert)
                .addOnSuccessListener(unused -> {
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onFailure(e.getMessage());
                });
    }

    /** Convenience overload without callback. */
    public void sendAlert(String message, String sender,
                          String keyword, String source, String appName) {
        sendAlert(message, sender, keyword, source, appName, null);
    }
}
