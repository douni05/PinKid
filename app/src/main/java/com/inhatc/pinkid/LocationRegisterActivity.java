package com.inhatc.pinkid;

import android.location.Geocoder;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

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

public class LocationRegisterActivity extends AppCompatActivity {

    private static final String DB_URL = "https://pinkid-1fec4-default-rtdb.asia-southeast1.firebasedatabase.app";

    private TextView txtSelectedAddress;
    private EditText editNickname;
    private LinearLayout locationsContainer;
    private TextView txtEmpty;
    private DatabaseReference db;
    private String parentUid;

    // 선택된 위치 key
    private String selectedLocationKey = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location_register);

        txtSelectedAddress  = findViewById(R.id.txtSelectedAddress);
        editNickname        = findViewById(R.id.editNickname);
        locationsContainer  = findViewById(R.id.locationsContainer);
        txtEmpty            = findViewById(R.id.txtEmpty);
        Button btnBack           = findViewById(R.id.btnBack);
        Button btnRegister       = findViewById(R.id.btnRegister);
        Button btnEditNickname   = findViewById(R.id.btnEditNickname);
        Button btnDeleteLocation = findViewById(R.id.btnDeleteLocation);

        db = FirebaseDatabase.getInstance(DB_URL).getReference();

        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) { finish(); return; }
        parentUid = auth.getCurrentUser().getUid();

        btnBack.setOnClickListener(v -> finish());

        // 주소 검색 (직접 입력 다이얼로그)
        txtSelectedAddress.setOnClickListener(v -> showAddressInputDialog());

        // 위치 등록
        btnRegister.setOnClickListener(v -> registerLocation());

        // 별명 수정
        btnEditNickname.setOnClickListener(v -> {
            if (selectedLocationKey == null) {
                Toast.makeText(this, "수정할 위치를 먼저 선택하세요", Toast.LENGTH_SHORT).show();
                return;
            }
            showEditNicknameDialog(selectedLocationKey);
        });

        // 위치 삭제
        btnDeleteLocation.setOnClickListener(v -> {
            if (selectedLocationKey == null) {
                Toast.makeText(this, "삭제할 위치를 먼저 선택하세요", Toast.LENGTH_SHORT).show();
                return;
            }
            showDeleteDialog(selectedLocationKey);
        });

        loadLocations();
    }

    private void showAddressInputDialog() {
        EditText input = new EditText(this);
        input.setHint("주소를 입력하세요");
        input.setPadding(48, 24, 48, 24);
        if (!txtSelectedAddress.getText().toString().equals("이곳을 터치하여 주소를 검색하세요")) {
            input.setText(txtSelectedAddress.getText().toString());
        }

        new AlertDialog.Builder(this)
                .setTitle("주소 입력")
                .setView(input)
                .setPositiveButton("확인", (dialog, which) -> {
                    String address = input.getText().toString().trim();
                    if (!address.isEmpty()) {
                        txtSelectedAddress.setText(address);
                        txtSelectedAddress.setTextColor(0xFF1A1A1A);
                    }
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void registerLocation() {
        String address  = txtSelectedAddress.getText().toString().trim();
        String nickname = editNickname.getText().toString().trim();

        if (address.equals("이곳을 터치하여 주소를 검색하세요") || address.isEmpty()) {
            Toast.makeText(this, "주소를 먼저 입력하세요", Toast.LENGTH_SHORT).show();
            return;
        }
        if (nickname.isEmpty()) {
            Toast.makeText(this, "별명을 입력하세요", Toast.LENGTH_SHORT).show();
            return;
        }

        // 주소 → 위도/경도 변환 (지오코딩)
        Geocoder geocoder = new Geocoder(this, Locale.KOREA);
        geocoder.getFromLocationName(address, 1, results -> {
            if (results != null && !results.isEmpty()) {
                double lat = results.get(0).getLatitude();
                double lng = results.get(0).getLongitude();
                saveLocation(address, nickname, lat, lng);
            } else {
                // 좌표 없음 → 사용자에게 확인 후 저장
                runOnUiThread(() ->
                    new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("위치 좌표를 찾지 못했습니다")
                        .setMessage("입력한 주소로 정확한 좌표를 찾지 못했습니다.\n" +
                                "이 위치는 저장되지만 이탈 알림이 동작하지 않습니다.\n\n" +
                                "그래도 저장하시겠습니까?")
                        .setPositiveButton("저장", (d, w) -> saveLocation(address, nickname, null, null))
                        .setNegativeButton("취소", null)
                        .show()
                );
            }
        });
    }

    private void saveLocation(String address, String nickname, Double lat, Double lng) {
        Map<String, Object> data = new HashMap<>();
        data.put("address", address);
        data.put("nickname", nickname);
        if (lat != null && lng != null) {
            data.put("latitude", lat);
            data.put("longitude", lng);
        }

        db.child("users").child(parentUid).child("registeredLocations").push()
                .setValue(data)
                .addOnSuccessListener(unused -> runOnUiThread(() -> {
                    Toast.makeText(this, "위치가 등록되었습니다", Toast.LENGTH_SHORT).show();
                    txtSelectedAddress.setText("이곳을 터치하여 주소를 검색하세요");
                    txtSelectedAddress.setTextColor(0xFF555555);
                    editNickname.setText("");
                    loadLocations();
                }))
                .addOnFailureListener(e -> runOnUiThread(() ->
                        Toast.makeText(this, "등록 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show()));
    }

    private void loadLocations() {
        db.child("users").child(parentUid).child("registeredLocations")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        locationsContainer.removeAllViews();
                        selectedLocationKey = null;

                        if (!snapshot.exists() || snapshot.getChildrenCount() == 0) {
                            txtEmpty.setVisibility(View.VISIBLE);
                            return;
                        }
                        txtEmpty.setVisibility(View.GONE);

                        for (DataSnapshot loc : snapshot.getChildren()) {
                            String key      = loc.getKey();
                            String address  = loc.child("address").getValue(String.class);
                            String nickname = loc.child("nickname").getValue(String.class);
                            addLocationRow(key, nickname, address);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        Toast.makeText(LocationRegisterActivity.this,
                                "목록을 불러오지 못했습니다", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void addLocationRow(String key, String nickname, String address) {
        // 행 레이아웃: 체크박스 + 세로 텍스트
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = (int)(8 * getResources().getDisplayMetrics().density);
        row.setLayoutParams(rowParams);

        // 체크박스
        CheckBox checkBox = new CheckBox(this);
        LinearLayout.LayoutParams cbParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cbParams.setMarginEnd((int)(4 * getResources().getDisplayMetrics().density));
        checkBox.setLayoutParams(cbParams);
        checkBox.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFF333333));
        checkBox.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isChecked) {
                // 다른 체크박스 해제
                for (int i = 0; i < locationsContainer.getChildCount(); i++) {
                    View child = locationsContainer.getChildAt(i);
                    if (child instanceof LinearLayout && child != row) {
                        CheckBox cb = (CheckBox) ((LinearLayout) child).getChildAt(0);
                        cb.setChecked(false);
                    }
                }
                selectedLocationKey = key;
            } else {
                if (key.equals(selectedLocationKey)) selectedLocationKey = null;
            }
        });

        // 텍스트 컬럼
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView tvNickname = new TextView(this);
        tvNickname.setText(nickname != null ? nickname : "");
        tvNickname.setTextSize(14);
        tvNickname.setTextColor(0xFF1A1A1A);
        tvNickname.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView tvAddress = new TextView(this);
        tvAddress.setText("주소 : " + (address != null ? address : ""));
        tvAddress.setTextSize(12);
        tvAddress.setTextColor(0xFF555555);

        textCol.addView(tvNickname);
        textCol.addView(tvAddress);

        row.addView(checkBox);
        row.addView(textCol);
        locationsContainer.addView(row);
    }

    private void showEditNicknameDialog(String key) {
        EditText input = new EditText(this);
        input.setHint("새 별명을 입력하세요");
        input.setPadding(48, 24, 48, 24);

        new AlertDialog.Builder(this)
                .setTitle("별명 수정")
                .setView(input)
                .setPositiveButton("수정", (dialog, which) -> {
                    String newNickname = input.getText().toString().trim();
                    if (newNickname.isEmpty()) {
                        Toast.makeText(this, "별명을 입력하세요", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    db.child("users").child(parentUid).child("registeredLocations")
                            .child(key).child("nickname").setValue(newNickname)
                            .addOnSuccessListener(unused -> {
                                Toast.makeText(this, "별명이 수정되었습니다", Toast.LENGTH_SHORT).show();
                                loadLocations();
                            });
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void showDeleteDialog(String key) {
        new AlertDialog.Builder(this)
                .setTitle("위치 삭제")
                .setMessage("선택한 위치를 삭제하시겠습니까?")
                .setPositiveButton("삭제", (dialog, which) -> {
                    db.child("users").child(parentUid).child("registeredLocations")
                            .child(key).removeValue()
                            .addOnSuccessListener(unused -> {
                                Toast.makeText(this, "위치가 삭제되었습니다", Toast.LENGTH_SHORT).show();
                                loadLocations();
                            });
                })
                .setNegativeButton("취소", null)
                .show();
    }
}
