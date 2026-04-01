package com.parentalcontrol.childapp.utils;

import java.util.List;

/**
 * KeywordChecker
 * ──────────────
 * Utility class that checks a message string against a list of
 * restricted keywords loaded from Firebase.
 *
 * Usage:
 *   String matched = KeywordChecker.findMatch(messageText, keywordList);
 *   if (matched != null) {
 *       // send alert to Firebase
 *   }
 */
public class KeywordChecker {

    private KeywordChecker() {
        // Static utility class – no instantiation needed
    }

    /**
     * Checks whether the given message contains any keyword from the list.
     *
     * Comparison is case-insensitive and checks for whole words as well as
     * substrings (e.g. "drugstore" also matches keyword "drug").
     *
     * @param message      The full message text to scan
     * @param keywords     List of restricted keywords (already lower-cased by FirebaseHelper)
     * @return             The first matching keyword, or null if no match found
     */
    public static String findMatch(String message, List<String> keywords) {
        if (message == null || message.trim().isEmpty()) return null;
        if (keywords == null || keywords.isEmpty()) return null;

        String lowerMessage = message.toLowerCase();

        for (String keyword : keywords) {
            if (keyword == null || keyword.trim().isEmpty()) continue;
            if (lowerMessage.contains(keyword.trim())) {
                return keyword.trim();   // Return the matched keyword
            }
        }
        return null;   // No match
    }

    /**
     * Returns true if the message contains at least one restricted keyword.
     *
     * @param message  The message text to scan
     * @param keywords List of restricted keywords
     */
    public static boolean containsRestricted(String message, List<String> keywords) {
        return findMatch(message, keywords) != null;
    }
}
