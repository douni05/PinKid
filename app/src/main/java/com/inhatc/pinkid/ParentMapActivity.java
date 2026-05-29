package com.inhatc.pinkid;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class ParentMapActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String DB_URL = "https://pinkid-1fec4-default-rtdb.asia-southeast1.firebasedatabase.app";

    private GoogleMap googleMap;
    private DatabaseReference db;
    private String parentUid;

    // 아이 UID → 마커
    private final Map<String, Marker> childMarkers = new HashMap<>();
    // 아이 UID → 위치 리스너
    private final Map<String, ValueEventListener> locationListeners = new HashMap<>();
    // 아이 UID → 이름
    private final Map<String, String> childNames = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent);

        db = FirebaseDatabase.getInstance(DB_URL).getReference();
        if (FirebaseAuth.getInstance().getCurrentUser() == null) { finish(); return; }
        parentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        SupportMapFragment mapFragment = (SupportMapFragment)
                getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(GoogleMap map) {
        googleMap = map;

        // 부모의 children 목록 읽기
        db.child("users").child(parentUid).child("children")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        for (DataSnapshot child : snapshot.getChildren()) {
                            String childUid = child.getKey();
                            loadChildNameAndListen(childUid);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {}
                });
    }

    private void loadChildNameAndListen(String childUid) {
        db.child("users").child(childUid).child("name")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        String name = snapshot.getValue(String.class);
                        childNames.put(childUid, name != null ? name : "아이");
                        startLocationListener(childUid);
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        childNames.put(childUid, "아이");
                        startLocationListener(childUid);
                    }
                });
    }

    private void startLocationListener(String childUid) {
        DatabaseReference locRef = db.child("location").child(childUid);

        ValueEventListener listener = locRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Double lat = snapshot.child("latitude").getValue(Double.class);
                Double lng = snapshot.child("longitude").getValue(Double.class);
                if (lat == null || lng == null) return;

                LatLng position = new LatLng(lat, lng);
                String childName = childNames.getOrDefault(childUid, "아이");

                if (!childMarkers.containsKey(childUid)) {
                    // 새 마커 생성
                    Marker marker = googleMap.addMarker(
                            new MarkerOptions().position(position).title(childName));
                    childMarkers.put(childUid, marker);

                    // 첫 마커면 카메라 이동
                    if (childMarkers.size() == 1) {
                        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 16f));
                    }
                } else {
                    // 기존 마커 위치 업데이트
                    childMarkers.get(childUid).setPosition(position);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {}
        });

        locationListeners.put(childUid, listener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (Map.Entry<String, ValueEventListener> entry : locationListeners.entrySet()) {
            db.child("location").child(entry.getKey()).removeEventListener(entry.getValue());
        }
    }
}
