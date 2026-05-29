package com.inhatc.pinkid;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Random;

public class LinkActivity extends AppCompatActivity {

    private static final String DB_URL = "https://pinkid-1fec4-default-rtdb.asia-southeast1.firebasedatabase.app";

    private DatabaseReference db;
    private String uid;
    private String role;

    // 부모 UI
    private LinearLayout childrenListContainer;
    private TextView tvNoChildren;

    // 아이 UI
    private TextView tvCode, tvLinkStatus;
    private Button btnHome;

    // 아이 linkedWith 변화 감지용
    private ValueEventListener linkedWithListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_link);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        db  = FirebaseDatabase.getInstance(DB_URL).getReference();

        db.child("users").child(uid).child("role")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        role = snapshot.getValue(String.class);
                        if ("parent".equals(role))       setupParentUI();
                        else if ("child".equals(role))   setupChildUI();
                        else                             showErrorDialog();
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        Toast.makeText(LinkActivity.this, "데이터 로드 실패", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ══════════════════════════ 부모 UI ══════════════════════════

    private void setupParentUI() {
        LinearLayout layoutParent = findViewById(R.id.layout_parent);
        EditText     etCode       = findViewById(R.id.et_code);
        Button       btnConnect   = findViewById(R.id.btn_connect);
        Button       btnDone      = findViewById(R.id.btn_done);
        childrenListContainer     = findViewById(R.id.children_list_container);
        tvNoChildren              = findViewById(R.id.tv_no_children);

        layoutParent.setVisibility(View.VISIBLE);
        loadConnectedChildren();

        btnConnect.setOnClickListener(v -> {
            String code = etCode.getText().toString().trim().toUpperCase();
            if (code.length() != 6) {
                Toast.makeText(this, "6자리 코드를 입력하세요", Toast.LENGTH_SHORT).show();
                return;
            }
            connectChild(code, etCode);
        });

        btnDone.setOnClickListener(v -> {
            startActivity(new Intent(this, ParentHomeActivity.class));
            finish();
        });
    }

    /** 코드로 child_codes 조회 → 중복 체크 → 연결 실행 */
    private void connectChild(String code, EditText etCode) {
        db.child("child_codes").child(code)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        String childUid = snapshot.getValue(String.class);
                        if (childUid == null) {
                            Toast.makeText(LinkActivity.this, "유효하지 않은 코드입니다", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        // 이미 나와 연결된 아이인지 확인
                        db.child("users").child(uid).child("children").child(childUid)
                                .addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(DataSnapshot snap) {
                                        if (snap.exists()) {
                                            Toast.makeText(LinkActivity.this,
                                                    "이미 연결된 아이입니다", Toast.LENGTH_SHORT).show();
                                            return;
                                        }
                                        checkAndConnect(childUid, etCode);
                                    }
                                    @Override
                                    public void onCancelled(DatabaseError error) {
                                        Toast.makeText(LinkActivity.this, "네트워크 오류가 발생했습니다", Toast.LENGTH_SHORT).show();
                                    }
                                });
                    }
                    @Override
                    public void onCancelled(DatabaseError error) {
                        Toast.makeText(LinkActivity.this, "네트워크 오류가 발생했습니다", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /** 다른 보호자와 연결 여부 확인 후 최종 연결 */
    private void checkAndConnect(String childUid, EditText etCode) {
        db.child("users").child(childUid).child("linkedWith")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        String existingParent = snapshot.getValue(String.class);
                        if (existingParent != null) {
                            Toast.makeText(LinkActivity.this,
                                    "이미 다른 보호자와 연결된 아이입니다", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        // 양방향 연결 저장
                        db.child("users").child(childUid).child("linkedWith").setValue(uid);
                        db.child("users").child(uid).child("children").child(childUid).setValue(true)
                                .addOnSuccessListener(unused -> {
                                    Toast.makeText(LinkActivity.this,
                                            "연결되었습니다!", Toast.LENGTH_SHORT).show();
                                    etCode.setText("");
                                    loadConnectedChildren();
                                });
                    }
                    @Override
                    public void onCancelled(DatabaseError error) {
                        Toast.makeText(LinkActivity.this, "네트워크 오류가 발생했습니다", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /** 현재 연결된 아이 목록 새로고침 */
    private void loadConnectedChildren() {
        db.child("users").child(uid).child("children")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        childrenListContainer.removeAllViews();
                        if (!snapshot.exists() || snapshot.getChildrenCount() == 0) {
                            tvNoChildren.setVisibility(View.VISIBLE);
                            return;
                        }
                        tvNoChildren.setVisibility(View.GONE);
                        for (DataSnapshot child : snapshot.getChildren()) {
                            addChildRow(child.getKey());
                        }
                    }
                    @Override
                    public void onCancelled(DatabaseError error) {
                        Toast.makeText(LinkActivity.this, "네트워크 오류가 발생했습니다", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /** 아이 1명 행(이름 + 연결 해제 버튼) 추가 */
    private void addChildRow(String childUid) {
        db.child("users").child(childUid).child("name")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        String name = snapshot.getValue(String.class);

                        // 행 컨테이너
                        LinearLayout row = new LinearLayout(LinkActivity.this);
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        row.setGravity(Gravity.CENTER_VERTICAL);
                        row.setPadding(dp(16), dp(14), dp(8), dp(14));

                        GradientDrawable bg = new GradientDrawable();
                        bg.setColor(0xFFD2EFD8);
                        bg.setCornerRadius(dp(10));
                        row.setBackground(bg);

                        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT);
                        rowParams.bottomMargin = dp(8);
                        row.setLayoutParams(rowParams);

                        // 아이 이름
                        TextView tvName = new TextView(LinkActivity.this);
                        tvName.setText(name != null ? name : "알 수 없음");
                        tvName.setTextSize(15);
                        tvName.setTextColor(0xFF1A1A1A);
                        tvName.setTypeface(null, Typeface.BOLD);
                        tvName.setLayoutParams(new LinearLayout.LayoutParams(
                                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                        // 연결 해제 버튼
                        Button btnDisconnect = new Button(LinkActivity.this);
                        btnDisconnect.setText("연결 해제");
                        btnDisconnect.setTextSize(12);
                        btnDisconnect.setTextColor(0xFFE53935);
                        btnDisconnect.setBackgroundTintList(
                                ColorStateList.valueOf(0x00000000));
                        btnDisconnect.setOnClickListener(v ->
                                showDisconnectDialog(childUid, name));

                        row.addView(tvName);
                        row.addView(btnDisconnect);
                        childrenListContainer.addView(row);
                    }
                    @Override
                    public void onCancelled(DatabaseError error) {
                        Toast.makeText(LinkActivity.this, "네트워크 오류가 발생했습니다", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showDisconnectDialog(String childUid, String childName) {
        String label = childName != null ? childName : "이 아이";
        new AlertDialog.Builder(this)
                .setTitle("연결 해제")
                .setMessage(label + "와(과) 연결을 해제하시겠습니까?")
                .setPositiveButton("해제", (dialog, which) -> disconnectChild(childUid))
                .setNegativeButton("취소", null)
                .show();
    }

    private void disconnectChild(String childUid) {
        db.child("users").child(childUid).child("linkedWith").removeValue();
        db.child("users").child(uid).child("children").child(childUid).removeValue()
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "연결이 해제되었습니다", Toast.LENGTH_SHORT).show();
                    loadConnectedChildren();
                });
    }

    // ══════════════════════════ 아이 UI ══════════════════════════

    private void setupChildUI() {
        LinearLayout layoutChild = findViewById(R.id.layout_child);
        tvCode       = findViewById(R.id.tv_code);
        tvLinkStatus = findViewById(R.id.tv_link_status);
        btnHome      = findViewById(R.id.btn_home);
        Button btnCopyCode = findViewById(R.id.btn_copy_code);

        layoutChild.setVisibility(View.VISIBLE);

        // 코드 클립보드 복사
        btnCopyCode.setOnClickListener(v -> {
            String code = tvCode.getText().toString();
            if (!code.isEmpty() && !code.equals("------")) {
                ClipboardManager clipboard =
                        (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("연결 코드", code));
                Toast.makeText(this, "코드가 복사되었습니다", Toast.LENGTH_SHORT).show();
            }
        });

        // 내 코드 불러오기
        db.child("users").child(uid).child("myCode")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        String code = snapshot.getValue(String.class);
                        if (code != null) {
                            tvCode.setText(code);
                        } else {
                            // 구버전 계정이거나 코드 누락 시 새로 생성
                            generateAndSaveCode();
                        }
                    }
                    @Override
                    public void onCancelled(DatabaseError error) {
                        Toast.makeText(LinkActivity.this, "네트워크 오류가 발생했습니다", Toast.LENGTH_SHORT).show();
                    }
                });

        // 부모가 연결할 때 감지
        listenForParentConnection();

        btnHome.setOnClickListener(v -> {
            startActivity(new Intent(this, ChildActivity.class));
            finish();
        });
    }

    /** 코드 없는 기존 계정 대응: 새 코드 생성 후 저장 */
    private void generateAndSaveCode() {
        String code = generateCode();
        tvCode.setText(code);
        db.child("users").child(uid).child("myCode").setValue(code);
        db.child("child_codes").child(code).setValue(uid);
    }

    /** linkedWith 변화 감지 → 연결 상태 UI 업데이트 */
    private void listenForParentConnection() {
        linkedWithListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                String parentUid = snapshot.getValue(String.class);
                if (parentUid != null) {
                    // 부모 이름 조회
                    db.child("users").child(parentUid).child("name")
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(DataSnapshot snap) {
                                    String parentName = snap.getValue(String.class);
                                    tvLinkStatus.setText(
                                            "✓ 연결됨: " + (parentName != null ? parentName : "보호자"));
                                    tvLinkStatus.setTextColor(0xFF2D7A4F);
                                    if (btnHome != null) btnHome.setVisibility(View.VISIBLE);
                                }
                                @Override
                                public void onCancelled(DatabaseError error) {
                        Toast.makeText(LinkActivity.this, "네트워크 오류가 발생했습니다", Toast.LENGTH_SHORT).show();
                    }
                            });
                } else {
                    tvLinkStatus.setText("연결 대기 중...");
                    tvLinkStatus.setTextColor(0xFFAAAAAA);
                    if (btnHome != null) btnHome.setVisibility(View.GONE);
                }
            }
            @Override
            public void onCancelled(DatabaseError error) {
                android.util.Log.w("PinKid", "linkedWith listener cancelled: " + error.getMessage());
            }
        };
        db.child("users").child(uid).child("linkedWith")
                .addValueEventListener(linkedWithListener);
    }

    // ══════════════════════════ 공통 ══════════════════════════

    private void showErrorDialog() {
        new AlertDialog.Builder(this)
                .setTitle("계정 오류")
                .setMessage("계정 정보를 불러오지 못했습니다.\n다시 로그인해주세요.")
                .setPositiveButton("확인", (dialog, which) -> {
                    FirebaseAuth.getInstance().signOut();
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

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (linkedWithListener != null && "child".equals(role)) {
            db.child("users").child(uid).child("linkedWith")
                    .removeEventListener(linkedWithListener);
        }
    }
}
