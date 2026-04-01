package com.parentalcontrol.childapp.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.parentalcontrol.childapp.R;

/**
 * LoginActivity
 * ─────────────
 * Entry point of the child app.
 * Uses Firebase Authentication (email + password).
 *
 * Flow:
 *   ┌──────────────────────────────────────────────┐
 *   │  If user already signed in → go to MainActivity │
 *   │  Else → show login form                         │
 *   └──────────────────────────────────────────────┘
 *
 * The parent app creates the child's account from the parent side
 * using the same Firebase project, so credentials are shared.
 */
public class LoginActivity extends AppCompatActivity {

    private EditText    etEmail, etPassword, etChildName;
    private Button      btnLogin;
    private ProgressBar progressBar;

    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();

        // If already logged in, skip straight to MainActivity
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            goToMain(currentUser.getDisplayName());
            return;
        }

        initViews();
        setupListeners();
    }

    private void initViews() {
        etEmail      = findViewById(R.id.et_email);
        etPassword   = findViewById(R.id.et_password);
        etChildName  = findViewById(R.id.et_child_name);
        btnLogin     = findViewById(R.id.btn_login);
        progressBar  = findViewById(R.id.progress_bar);
    }

    private void setupListeners() {
        btnLogin.setOnClickListener(v -> attemptLogin());
    }

    private void attemptLogin() {
        String email    = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String name     = etChildName.getText().toString().trim();

        // Basic validation
        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Enter email");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Enter password");
            return;
        }
        if (TextUtils.isEmpty(name)) {
            etChildName.setError("Enter child's name");
            return;
        }

        setLoading(true);

        // Sign in with Firebase Auth
        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    setLoading(false);
                    if (task.isSuccessful()) {
                        // Save child name to shared prefs for use in alerts
                        getSharedPreferences("prefs", MODE_PRIVATE)
                                .edit()
                                .putString("child_name", name)
                                .apply();

                        goToMain(name);
                    } else {
                        String err = task.getException() != null
                                ? task.getException().getMessage()
                                : "Authentication failed";
                        Toast.makeText(this, err, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void goToMain(String childName) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("child_name", childName);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!loading);
    }
}
