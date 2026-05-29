package com.inhatc.pinkid;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // [테스트용] 항상 로그인 화면으로 → 제출 전 아래 줄 삭제
        //FirebaseAuth.getInstance().signOut();

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null) {
            // 로그인 안 된 상태 → 로그인 화면
            goTo(LoginActivity.class);
        } else {
            // 로그인된 상태 → 역할/연결 확인 후 라우팅
            FirebaseDatabase.getInstance("https://pinkid-1fec4-default-rtdb.asia-southeast1.firebasedatabase.app").getReference("users")
                    .child(currentUser.getUid())
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot snapshot) {
                            String linkedWith = snapshot.child("linkedWith").getValue(String.class);
                            String role = snapshot.child("role").getValue(String.class);

                            if ("parent".equals(role)) {
                                // 부모는 연결 여부 상관없이 홈으로
                                goTo(ParentHomeActivity.class);
                            } else if ("child".equals(role)) {
                                // 아이는 연결 안 됐으면 코드 입력, 됐으면 아이 화면
                                goTo(linkedWith == null ? LinkActivity.class : ChildActivity.class);
                            } else {
                                goTo(LoginActivity.class);
                            }
                        }

                        @Override
                        public void onCancelled(DatabaseError error) {
                            goTo(LoginActivity.class);
                        }
                    });
        }
    }

    private void goTo(Class<?> cls) {
        startActivity(new Intent(this, cls));
        finish();
    }
}
