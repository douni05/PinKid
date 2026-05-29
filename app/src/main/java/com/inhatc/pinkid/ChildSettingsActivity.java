package com.inhatc.pinkid;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

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

    private TextView txtChildInfo, txtParentInfo;
    private DatabaseReference db;
    private String childUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_child_settings);

        txtChildInfo  = findViewById(R.id.txtChildInfo);
        txtParentInfo = findViewById(R.id.txtParentInfo);
        Button btnBack     = findViewById(R.id.btnBack);
        Button btnLogout   = findViewById(R.id.btnLogout);
        Button btnWithdraw = findViewById(R.id.btnWithdraw);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { finish(); return; }
        childUid = user.getUid();

        db = FirebaseDatabase.getInstance(DB_URL).getReference();

        btnBack.setOnClickListener(v -> finish());

        // 로그아웃: LocationService 중지 → signOut → 로그인 화면
        btnLogout.setOnClickListener(v -> {
            stopService(new Intent(this, LocationService.class));
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(ChildSettingsActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

        btnWithdraw.setOnClickListener(v -> showWithdrawDialog());

        loadData();
    }

    private void loadData() {
        db.child("users").child(childUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        String name = snapshot.child("name").getValue(String.class);
                        if (name != null) txtChildInfo.setText(name);

                        String parentUid = snapshot.child("linkedWith").getValue(String.class);
                        if (parentUid != null) {
                            loadParentName(parentUid);
                        } else {
                            txtParentInfo.setText("연결 안 됨");
                        }
                    }
                    @Override
                    public void onCancelled(DatabaseError error) {}
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
        if (user == null) return;

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
                            Intent intent = new Intent(ChildSettingsActivity.this, LoginActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            finish();
                        });
                    }
                    @Override
                    public void onCancelled(DatabaseError error) {}
                });
    }
}
