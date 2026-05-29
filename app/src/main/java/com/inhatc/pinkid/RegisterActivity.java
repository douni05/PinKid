package com.inhatc.pinkid;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class RegisterActivity extends AppCompatActivity {

    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        auth = FirebaseAuth.getInstance();

        EditText editTxtCreateID = findViewById(R.id.editTxtCreateID);
        EditText editTxtCreatePW = findViewById(R.id.editTxtCreatePW);
        EditText editTxtChkPW    = findViewById(R.id.editTxtChkPW);
        EditText editTxtName     = findViewById(R.id.editTxtName);
        CheckBox checkParent     = findViewById(R.id.checkParent);
        CheckBox checkChild      = findViewById(R.id.checkChild);
        Button btnSignUp         = findViewById(R.id.btnSignUp);
        Button btnGoLogin        = findViewById(R.id.btnGoLogin);

        // 체크박스 상호 배타적으로 동작
        checkParent.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isChecked) checkChild.setChecked(false);
        });
        checkChild.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isChecked) checkParent.setChecked(false);
        });

        btnGoLogin.setOnClickListener(v -> finish());

        btnSignUp.setOnClickListener(v -> {
            String email    = editTxtCreateID.getText().toString().trim();
            String password = editTxtCreatePW.getText().toString().trim();
            String pwCheck  = editTxtChkPW.getText().toString().trim();
            String name     = editTxtName.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty() || pwCheck.isEmpty() || name.isEmpty()) {
                Toast.makeText(this, "모든 항목을 입력하세요", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!password.equals(pwCheck)) {
                Toast.makeText(this, "비밀번호가 일치하지 않습니다", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!checkParent.isChecked() && !checkChild.isChecked()) {
                Toast.makeText(this, "역할을 선택하세요 (학부모 또는 아이)", Toast.LENGTH_SHORT).show();
                return;
            }

            String role = checkParent.isChecked() ? "parent" : "child";

            auth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener(result -> {
                        String uid = result.getUser().getUid();
                        DatabaseReference db = FirebaseDatabase
                                .getInstance("https://pinkid-1fec4-default-rtdb.asia-southeast1.firebasedatabase.app")
                                .getReference();

                        Map<String, Object> userData = new HashMap<>();
                        userData.put("name", name);
                        userData.put("email", email);
                        userData.put("role", role);

                        // 아이 계정이면 고유 연결 코드 생성
                        String childCode = "child".equals(role) ? generateCode() : null;
                        if (childCode != null) {
                            userData.put("myCode", childCode);
                        }

                        db.child("users").child(uid).setValue(userData)
                                .addOnSuccessListener(unused -> {
                                    // child_codes 조회 테이블에도 저장
                                    if (childCode != null) {
                                        db.child("child_codes").child(childCode).setValue(uid);
                                    }
                                    auth.signOut();
                                    new AlertDialog.Builder(this)
                                            .setTitle("가입 완료")
                                            .setMessage("회원가입이 완료되었습니다.\n로그인 화면으로 이동합니다.")
                                            .setPositiveButton("확인", (dialog, which) -> {
                                                Intent intent = new Intent(this, LoginActivity.class);
                                                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                                startActivity(intent);
                                                finish();
                                            })
                                            .setCancelable(false)
                                            .show();
                                });
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "가입 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        });
    }

    private String generateCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random = new Random();
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
