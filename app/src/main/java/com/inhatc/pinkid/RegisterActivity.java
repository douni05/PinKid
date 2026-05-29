package com.inhatc.pinkid;

import android.content.Intent;
import android.util.Patterns;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class RegisterActivity extends AppCompatActivity {

    private static final String DB_URL = "https://pinkid-1fec4-default-rtdb.asia-southeast1.firebasedatabase.app";

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
        Button   btnSignUp       = findViewById(R.id.btnSignUp);
        Button   btnGoLogin      = findViewById(R.id.btnGoLogin);

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
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "올바른 이메일 형식을 입력하세요", Toast.LENGTH_SHORT).show();
                return;
            }
            if (password.length() < 6) {
                Toast.makeText(this, "비밀번호는 6자리 이상이어야 합니다", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!password.equals(pwCheck)) {
                Toast.makeText(this, "비밀번호가 일치하지 않습니다", Toast.LENGTH_SHORT).show();
                return;
            }
            if (name.length() > 20) {
                Toast.makeText(this, "이름은 20자 이내로 입력하세요", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!checkParent.isChecked() && !checkChild.isChecked()) {
                Toast.makeText(this, "역할을 선택하세요 (학부모 또는 아이)", Toast.LENGTH_SHORT).show();
                return;
            }

            String role = checkParent.isChecked() ? "parent" : "child";

            auth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener(result -> {
                        if (result.getUser() == null) {
                            Toast.makeText(this, "가입 중 오류가 발생했습니다. 다시 시도하세요.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        String uid = result.getUser().getUid();
                        DatabaseReference db = FirebaseDatabase.getInstance(DB_URL).getReference();

                        if ("child".equals(role)) {
                            // 아이: 중복 없는 고유 코드 생성 후 저장
                            saveChildWithUniqueCode(uid, name, email, db, 0);
                        } else {
                            // 보호자: 코드 불필요
                            saveUser(uid, name, email, "parent", null, db);
                        }
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "가입 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        });
    }

    /** 중복 없는 코드를 찾을 때까지 최대 10회 재시도 */
    private void saveChildWithUniqueCode(String uid, String name, String email,
                                          DatabaseReference db, int attempt) {
        if (attempt >= 10) {
            Toast.makeText(this, "코드 생성에 실패했습니다. 다시 시도하세요.", Toast.LENGTH_SHORT).show();
            // 인증 계정 롤백
            if (auth.getCurrentUser() != null) auth.getCurrentUser().delete();
            return;
        }

        String code = generateCode();
        db.child("child_codes").child(code)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            // 코드 충돌 → 재시도
                            saveChildWithUniqueCode(uid, name, email, db, attempt + 1);
                        } else {
                            // 사용 가능한 코드 확보 → 저장
                            saveUser(uid, name, email, "child", code, db);
                        }
                    }
                    @Override
                    public void onCancelled(DatabaseError error) {
                        Toast.makeText(RegisterActivity.this,
                                "오류가 발생했습니다. 다시 시도하세요.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void saveUser(String uid, String name, String email,
                          String role, String childCode, DatabaseReference db) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("name", name);
        userData.put("email", email);
        userData.put("role", role);
        if (childCode != null) userData.put("myCode", childCode);

        db.child("users").child(uid).setValue(userData)
                .addOnSuccessListener(unused -> {
                    if (childCode != null) {
                        // child_codes 조회 테이블에도 저장 (원자적으로 처리)
                        db.child("child_codes").child(childCode).setValue(uid);
                    }
                    auth.signOut();
                    showSuccessDialog();
                })
                .addOnFailureListener(e -> {
                    // DB 저장 실패 시 Auth 계정 롤백
                    if (auth.getCurrentUser() != null) auth.getCurrentUser().delete();
                    Toast.makeText(this, "저장 중 오류가 발생했습니다. 다시 시도하세요.", Toast.LENGTH_SHORT).show();
                });
    }

    private void showSuccessDialog() {
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
