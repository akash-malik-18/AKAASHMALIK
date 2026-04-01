package com.parentalcontrol.childapp.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

import com.parentalcontrol.childapp.utils.FirebaseHelper;
import com.parentalcontrol.childapp.utils.KeywordChecker;

import java.util.List;

/**
 * SmsReceiver
 * ───────────
 * BroadcastReceiver that intercepts incoming SMS messages.
 *
 * When an SMS arrives:
 *   1. Parse all message parts into full text.
 *   2. Fetch restricted keywords from Firebase.
 *   3. If a keyword is found → push an alert to Firebase.
 *
 * Registered in AndroidManifest.xml with priority 999 so it runs
 * before the stock SMS app.
 *
 * Requires permissions:
 *   RECEIVE_SMS  (declared in Manifest)
 *   READ_SMS     (declared in Manifest, requested at runtime for API 23+)
 */
public class SmsReceiver extends BroadcastReceiver {

    private static final String TAG = "SmsReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {

        if (!android.provider.Telephony.Sms.Intents.SMS_RECEIVED_ACTION
                .equals(intent.getAction())) {
            return;
        }

        // ── Parse SMS PDUs ───────────────────────────────────────────────────
        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        Object[] pdus = (Object[]) bundle.get("pdus");
        if (pdus == null || pdus.length == 0) return;

        String format = bundle.getString("format");

        StringBuilder fullMessage = new StringBuilder();
        String senderPhone = "";

        for (Object pdu : pdus) {
            SmsMessage sms = SmsMessage.createFromPdu((byte[]) pdu, format);
            if (sms != null) {
                fullMessage.append(sms.getMessageBody());
                senderPhone = sms.getOriginatingAddress(); // phone number of sender
            }
        }

        final String messageText = fullMessage.toString().trim();
        final String sender      = (senderPhone != null) ? senderPhone : "Unknown";

        Log.d(TAG, "SMS received from " + sender + ": " + messageText);

        // ── Check keywords ───────────────────────────────────────────────────
        FirebaseHelper.getInstance().fetchKeywords(new FirebaseHelper.KeywordsCallback() {
            @Override
            public void onKeywordsLoaded(List<String> keywords) {
                String matchedKeyword = KeywordChecker.findMatch(messageText, keywords);
                if (matchedKeyword != null) {
                    Log.d(TAG, "Restricted keyword found: " + matchedKeyword);

                    // Push alert to Firebase
                    FirebaseHelper.getInstance().sendAlert(
                            messageText,
                            sender,
                            matchedKeyword,
                            "SMS",
                            "SMS",
                            new FirebaseHelper.AlertCallback() {
                                @Override
                                public void onSuccess() {
                                    Log.d(TAG, "Alert sent to Firebase successfully.");
                                }

                                @Override
                                public void onFailure(String error) {
                                    Log.e(TAG, "Failed to send alert: " + error);
                                }
                            }
                    );
                }
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to load keywords: " + error);
            }
        });
    }
}
