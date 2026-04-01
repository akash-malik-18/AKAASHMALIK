package com.parentalcontrol.childapp.activities;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.parentalcontrol.childapp.R;
import com.parentalcontrol.childapp.models.ChatMessage;
import com.parentalcontrol.childapp.utils.FirebaseHelper;
import com.parentalcontrol.childapp.utils.KeywordChecker;

import java.util.ArrayList;
import java.util.List;

/**
 * ChatActivity
 * ────────────
 * In-app chat screen for the child.
 *
 * Features:
 *   - Real-time chat via Firebase Realtime Database (/chat/ node).
 *   - Every sent AND received message is scanned for restricted keywords.
 *   - If a keyword is detected → alert is pushed to /alerts/ automatically.
 *   - Messages are displayed in a RecyclerView (sent = right, received = left).
 *
 * Firebase chat structure:
 *   /chat/{push_id}/
 *       text      : message text
 *       sender    : display name
 *       senderId  : Firebase UID
 *       timestamp : epoch millis
 */
public class ChatActivity extends AppCompatActivity {

    private RecyclerView      recyclerView;
    private EditText          etMessage;
    private ImageButton       btnSend;
    private ChatAdapter       adapter;

    private final List<ChatMessage> messages = new ArrayList<>();
    private List<String>      keywords = new ArrayList<>();

    private DatabaseReference chatRef;
    private ChildEventListener chatListener;
    private ValueEventListener keywordListener;

    private String childName;
    private String childUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        // Get child identity
        childUid  = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "unknown";
        childName = getSharedPreferences("prefs", MODE_PRIVATE)
                .getString("child_name", "Child");

        // Firebase chat node
        chatRef = FirebaseDatabase.getInstance().getReference("chat");

        initViews();
        loadKeywords();
        listenForMessages();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (chatListener != null)    chatRef.removeEventListener(chatListener);
        if (keywordListener != null) FirebaseHelper.getInstance()
                                            .removeKeywordListener(keywordListener);
    }

    // ── View setup ───────────────────────────────────────────────────────────

    private void initViews() {
        recyclerView = findViewById(R.id.recycler_chat);
        etMessage    = findViewById(R.id.et_message);
        btnSend      = findViewById(R.id.btn_send);

        adapter = new ChatAdapter(messages, childUid);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        btnSend.setOnClickListener(v -> sendMessage());
    }

    // ── Keyword loading ──────────────────────────────────────────────────────

    private void loadKeywords() {
        // Keep keywords in sync live so new keywords take effect immediately
        keywordListener = FirebaseHelper.getInstance()
                .listenForKeywords(new FirebaseHelper.KeywordsCallback() {
                    @Override
                    public void onKeywordsLoaded(List<String> updatedKeywords) {
                        keywords = updatedKeywords;
                    }

                    @Override
                    public void onError(String error) {
                        // Non-fatal – chat still works, just no keyword alerts
                    }
                });
    }

    // ── Message sending ──────────────────────────────────────────────────────

    private void sendMessage() {
        String text = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;

        // Check keyword BEFORE sending
        String matchedKeyword = KeywordChecker.findMatch(text, keywords);
        if (matchedKeyword != null) {
            // Alert parent
            FirebaseHelper.getInstance().sendAlert(
                    text, childName, matchedKeyword, "InApp", "ChildApp"
            );
        }

        // Build message object
        ChatMessage msg = new ChatMessage(text, childName, System.currentTimeMillis(), true);

        // Push to Firebase /chat/
        chatRef.push().setValue(buildChatMap(text));

        etMessage.setText("");

        // Scroll to bottom
        recyclerView.scrollToPosition(messages.size() - 1);
    }

    private java.util.HashMap<String, Object> buildChatMap(String text) {
        java.util.HashMap<String, Object> map = new java.util.HashMap<>();
        map.put("text",      text);
        map.put("sender",    childName);
        map.put("senderId",  childUid);
        map.put("timestamp", System.currentTimeMillis());
        return map;
    }

    // ── Message receiving ────────────────────────────────────────────────────

    private void listenForMessages() {
        chatListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot,
                                     String previousChildName) {
                String text     = snapshot.child("text").getValue(String.class);
                String sender   = snapshot.child("sender").getValue(String.class);
                String senderId = snapshot.child("senderId").getValue(String.class);
                Long   ts       = snapshot.child("timestamp").getValue(Long.class);

                if (text == null) return;

                boolean isMine = childUid.equals(senderId);
                ChatMessage msg = new ChatMessage(
                        text,
                        sender != null ? sender : "Unknown",
                        ts != null ? ts : System.currentTimeMillis(),
                        isMine
                );

                messages.add(msg);
                adapter.notifyItemInserted(messages.size() - 1);
                recyclerView.scrollToPosition(messages.size() - 1);

                // Also check incoming messages for keywords
                if (!isMine) {
                    String matchedKeyword = KeywordChecker.findMatch(text, keywords);
                    if (matchedKeyword != null) {
                        FirebaseHelper.getInstance().sendAlert(
                                text,
                                sender != null ? sender : "Unknown",
                                matchedKeyword,
                                "InApp",
                                "ChildApp"
                        );
                    }
                }
            }

            @Override public void onChildChanged(@NonNull DataSnapshot s, String p) {}
            @Override public void onChildRemoved(@NonNull DataSnapshot s) {}
            @Override public void onChildMoved(@NonNull DataSnapshot s, String p) {}
            @Override public void onCancelled(@NonNull DatabaseError e) {
                Toast.makeText(ChatActivity.this,
                        "Chat error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };

        chatRef.addChildEventListener(chatListener);
    }

    // ── RecyclerView Adapter ─────────────────────────────────────────────────

    static class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {

        private static final int VIEW_TYPE_SENT     = 1;
        private static final int VIEW_TYPE_RECEIVED = 2;

        private final List<ChatMessage> messages;
        private final String            myUid;

        ChatAdapter(List<ChatMessage> messages, String myUid) {
            this.messages = messages;
            this.myUid    = myUid;
        }

        @Override
        public int getItemViewType(int position) {
            return messages.get(position).isMine()
                    ? VIEW_TYPE_SENT : VIEW_TYPE_RECEIVED;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int layout = viewType == VIEW_TYPE_SENT
                    ? R.layout.item_message_sent
                    : R.layout.item_message_received;
            View view = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ChatMessage msg = messages.get(position);
            holder.tvMessage.setText(msg.getText());
            holder.tvSender.setText(msg.getSender());
        }

        @Override
        public int getItemCount() { return messages.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvMessage, tvSender;
            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvMessage = itemView.findViewById(R.id.tv_message);
                tvSender  = itemView.findViewById(R.id.tv_sender);
            }
        }
    }
}
