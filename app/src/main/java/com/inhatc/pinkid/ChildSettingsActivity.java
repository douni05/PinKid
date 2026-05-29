package com.inhatc.pinkid;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class ChildSettingsActivity extends AppCompatActivity {

    private static final String DB_URL = "https://pinkid-1fec4-default-rtdb.asia-southeast1.firebasedatabase.app";

    private TextView txtChildInfo, txtParentInfo, txtMyCode;
    private DatabaseReference db;
    private String childUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_child_settings);

        txtChildInfo  = findViewById(R.id.txtChildInfo);
        txtParentInfo = findViewById(R.id.txtParentInfo);
        txtMyCode     = findViewById(R.id.txtMyCode);
        Button btnBack     = findViewById(R.id.btnBack);
        Button btnCopyCode = findViewById(R.id.btnCopyCode);
        Button btnLogout   = findViewById(R.id.btnLogout);
        Button btnWithdraw = findViewById(R.id.btnWithdraw);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        childUid = user.getUid();
        db = FirebaseDatabase.getInstance(DB_URL).getReference();

        btnBack.setOnClickListener(v -> finish());

        btnCopyCode.setOnClickListener(v -> {
            String code = txtMyCode.getText().toString();
            if (!code.isEmpty() && !code.equals("------")) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("연결 코드", code));
                Toast.makeText(this, "코드가 복사되었습니다", Toast.LENGTH_SHORT).show();
            }
        });

        btnLogout.setOnClickListener(v -> showLogoutDialog());
        btnWithdraw.setOnClickListener(v -> showWithdrawDialog());

        loadData();
    }

    // ─────────────────────── 데이터 로드 ───────────────────────

    private void loadData() {
        db.child("users").child(childUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        String name = snapshot.child("name").getValue(String.class);
                        txtChildInfo.setText(name != null ? name : "알 수 없음");

                        String code = snapshot.child("myCode").getValue(String.class);
                        txtMyCode.setText(code != null ? code : "------");

                        String parentUid = snapshot.child("linkedWith").getValue(String.class);
                        if (parentUid != null) {
                            loadParentName(parentUid);
                        } else {
                            txtParentInfo.setText("연결 안 됨");
                        }
                    }
                    @Override
                    public void onCancelled(DatabaseError error) {
                        Toast.makeText(ChildSettingsActivity.this,
                                "정보를 불러오지 못했습니다", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loadParentName(String parentUid) {
        db.child("users").child(parentUid).child("name")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        String name = snapshot.getValue(String.class);
                        txtParentInfo.setText(name != null ? name : "알 수 없음");
                    }
                    @Override
                    public void onCancelled(DatabaseError error) {
                        txtParentInfo.setText("알 수 없음");
                    }
                });
    }

    // ─────────────────────── 로그아웃 ───────────────────────

    private void showLogoutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("로그아웃")
                .setMessage("로그아웃 하시겠습니까?")
                .setPositiveButton("로그아웃", (dialog, which) -> {
                    stopService(new Intent(this, LocationService.class));
                    FirebaseAuth.getInstance().signOut();
                    Intent intent = new Intent(ChildSettingsActivity.this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    // ─────────────────────── 탈퇴하기 ───────────────────────

    private void showWithdrawDialog() {
        new AlertDialog.Builder(this)
                .setTitle("탈퇴하기")
                .setMessage("정말 탈퇴하시겠습니까?\n모든 데이터가 삭제됩니다.")
                .setPositiveButton("탈퇴", (dialog, which) -> withdraw())
                .setNegativeButton("취소", null)
                .show();
    }

    private void withdraw() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "이미 로그아웃된 상태입니다", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        db.child("users").child(childUid).child("linkedWith")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        String parentUid = snapshot.getValue(String.class);
                        if (parentUid != null) {
                            db.child("users").child(parentUid)
                                    .child("children").child(childUid).removeValue();
                        }
                        db.child("users").child(childUid).removeValue();
                        db.child("location").child(childUid).removeValue();

                        stopService(new Intent(ChildSettingsActivity.this, LocationService.class));

                        user.delete().addOnCompleteListener(task -> {
                            if (!task.isSuccessful()) {
                                // 인증 만료 등으로 삭제 실패 시에도 로그아웃 처리
                                FirebaseAuth.getInstance().signOut();
                            }
                            Intent intent = new Intent(ChildSettingsActivity.this, LoginActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            finish();
                        });
                    }
                    @Override
                    public void onCancelled(DatabaseError error) {
                        // DB 정리 실패해도 계정은 삭제 진행
                        stopService(new Intent(ChildSettingsActivity.this, LocationService.class));
                        user.delete().addOnCompleteListener(task -> {
                            FirebaseAuth.getInstance().signOut();
                            Intent intent = new Intent(ChildSettingsActivity.this, LoginActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            finish();
                        });
                    }
                });
    }
}
