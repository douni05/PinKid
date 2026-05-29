package com.inhatc.pinkid;

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

public class SettingsActivity extends AppCompatActivity {

    private static final String DB_URL = "https://pinkid-1fec4-default-rtdb.asia-southeast1.firebasedatabase.app";

    private TextView txtParentInfo, txtChildInfo;
    private DatabaseReference db;
    private String parentUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        txtParentInfo = findViewById(R.id.txtParentInfo);
        txtChildInfo  = findViewById(R.id.txtChildInfo);
        Button btnBack     = findViewById(R.id.btnBack);
        Button btnLogout   = findViewById(R.id.btnLogout);
        Button btnWithdraw = findViewById(R.id.btnWithdraw);

        db = FirebaseDatabase.getInstance(DB_URL).getReference();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finish();
            return;
        }
        parentUid = user.getUid();

        btnBack.setOnClickListener(v -> finish());

        btnLogout.setOnClickListener(v -> showLogoutDialog());

        btnWithdraw.setOnClickListener(v -> showWithdrawDialog());

        loadData();
    }

    private void loadData() {
        db.child("users").child(parentUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        // 학부모 이름
                        String name = snapshot.child("name").getValue(String.class);
                        if (name != null) txtParentInfo.setText(name);

                        // 연결된 아이 목록
                        DataSnapshot childrenSnap = snapshot.child("children");
                        long count = childrenSnap.getChildrenCount();

                        if (count == 0) {
                            txtChildInfo.setText("없음");
                        } else {
                            loadChildNames(childrenSnap);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        Toast.makeText(SettingsActivity.this,
                                "정보를 불러오지 못했습니다", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loadChildNames(DataSnapshot childrenSnap) {
        final long total = childrenSnap.getChildrenCount();
        final StringBuilder names = new StringBuilder();
        final long[] loaded = {0};

        for (DataSnapshot child : childrenSnap.getChildren()) {
            String childUid = child.getKey();
            db.child("users").child(childUid).child("name")
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot snap) {
                            String cName = snap.getValue(String.class);
                            if (cName != null) {
                                if (names.length() > 0) names.append(", ");
                                names.append(cName);
                            }
                            loaded[0]++;
                            if (loaded[0] == total) {
                                txtChildInfo.setText(names.length() > 0 ? names.toString() : "없음");
                            }
                        }

                        @Override
                        public void onCancelled(DatabaseError error) {
                            loaded[0]++;
                            if (loaded[0] == total) {
                                txtChildInfo.setText(names.length() > 0 ? names.toString() : "없음");
                            }
                        }
                    });
        }
    }

    private void showLogoutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("로그아웃")
                .setMessage("로그아웃 하시겠습니까?")
                .setPositiveButton("로그아웃", (dialog, which) -> {
                    FirebaseAuth.getInstance().signOut();
                    Intent intent = new Intent(SettingsActivity.this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("취소", null)
                .show();
    }

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

        db.child("users").child(parentUid).child("children")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        for (DataSnapshot child : snapshot.getChildren()) {
                            String childUid = child.getKey();
                            if (childUid != null) {
                                db.child("users").child(childUid).child("linkedWith").removeValue();
                            }
                        }
                        db.child("users").child(parentUid).removeValue();
                        db.child("location").child(parentUid).removeValue();

                        user.delete().addOnCompleteListener(task -> {
                            if (!task.isSuccessful()) {
                                FirebaseAuth.getInstance().signOut();
                            }
                            Intent intent = new Intent(SettingsActivity.this, LoginActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            finish();
                        });
                    }
                    @Override
                    public void onCancelled(DatabaseError error) {
                        // DB 정리 실패해도 계정 삭제 진행
                        db.child("users").child(parentUid).removeValue();
                        user.delete().addOnCompleteListener(task -> {
                            if (!task.isSuccessful()) FirebaseAuth.getInstance().signOut();
                            Intent intent = new Intent(SettingsActivity.this, LoginActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            finish();
                        });
                    }
                });
    }
}
