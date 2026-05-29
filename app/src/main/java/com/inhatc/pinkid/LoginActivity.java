package com.inhatc.pinkid;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class LoginActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private String captchaToken = null;
    private TextView txtCaptchaStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();

        EditText editTxtID     = findViewById(R.id.editTxtID);
        EditText editTxtPW     = findViewById(R.id.editTxtPW);
        Button btnLogin        = findViewById(R.id.btnLogin);
        Button btnSignUp       = findViewById(R.id.btnSignUp);
        Button btnCaptcha      = findViewById(R.id.btnCaptcha);
        txtCaptchaStatus       = findViewById(R.id.txtCaptchaStatus);

        btnCaptcha.setOnClickListener(v -> showCaptchaDialog());

        btnLogin.setOnClickListener(v -> {
            String email    = editTxtID.getText().toString().trim();
            String password = editTxtPW.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "이메일과 비밀번호를 입력하세요", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "올바른 이메일 형식을 입력하세요", Toast.LENGTH_SHORT).show();
                return;
            }
            if (captchaToken == null) {
                Toast.makeText(this, "보안 문자를 완료해 주세요", Toast.LENGTH_SHORT).show();
                return;
            }

            auth.signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener(result -> {
                        if (result.getUser() == null) {
                            Toast.makeText(this, "로그인에 실패했습니다. 다시 시도하세요.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        String uid = result.getUser().getUid();
                        FirebaseDatabase.getInstance("https://pinkid-1fec4-default-rtdb.asia-southeast1.firebasedatabase.app")
                                .getReference("users").child(uid)
                                .addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(DataSnapshot snapshot) {
                                        String role       = snapshot.child("role").getValue(String.class);
                                        String linkedWith = snapshot.child("linkedWith").getValue(String.class);

                                        Class<?> target;
                                        if ("parent".equals(role)) {
                                            target = ParentHomeActivity.class;
                                        } else if ("child".equals(role)) {
                                            target = linkedWith == null ? LinkActivity.class : ChildActivity.class;
                                        } else {
                                            target = LoginActivity.class;
                                        }

                                        Intent intent = new Intent(LoginActivity.this, target);
                                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                        startActivity(intent);
                                        finish();
                                    }
                                    @Override
                                    public void onCancelled(DatabaseError error) {
                                        Toast.makeText(LoginActivity.this,
                                                "네트워크 오류가 발생했습니다. 다시 시도하세요.", Toast.LENGTH_SHORT).show();
                                    }
                                });
                    })
                    .addOnFailureListener(e -> {
                        String msg = parseFirebaseAuthError(e.getMessage());
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                        captchaToken = null;
                        txtCaptchaStatus.setText("");
                        Button btn = findViewById(R.id.btnCaptcha);
                        btn.setText("🤖 보안 문자 인증하기");
                    });
        });

        btnSignUp.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void showCaptchaDialog() {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_captcha);

        WebView webView = dialog.findViewById(R.id.webViewDialog);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);

        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void onCaptchaSuccess(String token) {
                captchaToken = token;
                runOnUiThread(() -> {
                    txtCaptchaStatus.setText("");
                    Button btn = findViewById(R.id.btnCaptcha);
                    btn.setText("✅ 인증 완료");
                    dialog.dismiss();
                });
            }

            @JavascriptInterface
            public void onCaptchaExpired() {
                captchaToken = null;
            }

            @JavascriptInterface
            public void onChallengeOpen() {
                // 이미지 선택 챌린지 등장 — 별도 처리 없음
            }

            @JavascriptInterface
            public void onChallengeClose() {
                // 챌린지 닫힘 — 성공 시 dismiss는 onCaptchaSuccess에서 처리
            }
        }, "Android");

        webView.loadUrl("https://pinkid-1fec4.web.app/recaptcha.html");
        dialog.show();
    }

    private String parseFirebaseAuthError(String errorMessage) {
        if (errorMessage == null) return "로그인에 실패했습니다.";
        if (errorMessage.contains("no user record") ||
            errorMessage.contains("user-not-found"))    return "등록되지 않은 이메일입니다.";
        if (errorMessage.contains("password is invalid") ||
            errorMessage.contains("wrong-password"))    return "비밀번호가 올바르지 않습니다.";
        if (errorMessage.contains("too-many-requests")) return "잠시 후 다시 시도해 주세요.";
        if (errorMessage.contains("network"))           return "네트워크를 확인해 주세요.";
        return "로그인에 실패했습니다.";
    }
}
