package com.inhatc.pinkid;

import android.content.Intent;
import android.os.Bundle;
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

import java.util.Random;

public class LoginActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private TextView txtCaptcha;
    private String currentCaptcha;

    private static final String CAPTCHA_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();

        EditText editTxtID      = findViewById(R.id.editTxtID);
        EditText editTxtPW      = findViewById(R.id.editTxtPW);
        EditText editTxtCaptcha = findViewById(R.id.editTxtCaptcha);
        Button btnLogin         = findViewById(R.id.btnLogin);
        Button btnSignUp        = findViewById(R.id.btnSignUp);
        TextView btnRefresh     = findViewById(R.id.btnRefreshCaptcha);
        txtCaptcha              = findViewById(R.id.txtCaptcha);

        refreshCaptcha();

        btnRefresh.setOnClickListener(v -> refreshCaptcha());

        btnLogin.setOnClickListener(v -> {
            String email    = editTxtID.getText().toString().trim();
            String password = editTxtPW.getText().toString().trim();
            String captcha  = editTxtCaptcha.getText().toString().trim().toUpperCase();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "이메일과 비밀번호를 입력하세요", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!captcha.equals(currentCaptcha)) {
                Toast.makeText(this, "보안 문자가 일치하지 않습니다", Toast.LENGTH_SHORT).show();
                refreshCaptcha();
                editTxtCaptcha.setText("");
                return;
            }

            auth.signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener(result -> {
                        String uid = result.getUser().getUid();
                        // 역할 확인 후 직접 라우팅 (SplashActivity 우회)
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
                                        Toast.makeText(LoginActivity.this, "오류가 발생했습니다", Toast.LENGTH_SHORT).show();
                                    }
                                });
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "로그인 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        refreshCaptcha();
                        editTxtCaptcha.setText("");
                    });
        });

        btnSignUp.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void refreshCaptcha() {
        currentCaptcha = generateCaptchaString(5);
        txtCaptcha.setText(currentCaptcha);
    }

    private String generateCaptchaString(int length) {
        Random random = new Random();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CAPTCHA_CHARS.charAt(random.nextInt(CAPTCHA_CHARS.length())));
        }
        return sb.toString();
    }
}
