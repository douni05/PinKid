package com.inhatc.pinkid;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.location.Geocoder;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;

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
import java.util.Locale;
import java.util.Map;

public class ParentHomeActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String DB_URL = "https://pinkid-1fec4-default-rtdb.asia-southeast1.firebasedatabase.app";
    private static final String CHANNEL_ID = "pinkid_geofence";
    private static final double GEOFENCE_RADIUS_METERS = 300.0;

    private TextView txtGreetingName, txtChildName, txtChildZone, txtCurrentAddress;
    private DatabaseReference db;
    private String parentUid;

    private GoogleMap miniMap;
    // 아이 UID → 마커
    private final Map<String, Marker> childMarkers = new HashMap<>();
    // 아이 UID → 위치 리스너
    private final Map<String, ValueEventListener> locationListeners = new HashMap<>();
    // 아이 UID → 이름
    private final Map<String, String> childNames = new HashMap<>();

    // 등록된 위치: locationKey → [lat, lng]
    private final Map<String, double[]> registeredLocations = new HashMap<>();
    // 등록된 위치 별명: locationKey → nickname
    private final Map<String, String> locationNicknames = new HashMap<>();
    // 지오펜스 상태: "childUid_locationKey" → 현재 존 안에 있는지 여부
    private final Map<String, Boolean> geofenceState = new HashMap<>();

    private ValueEventListener registeredLocationsListener;
    private int notificationId = 2000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent_home);

        txtGreetingName   = findViewById(R.id.txtGreetingName);
        txtChildName      = findViewById(R.id.txtChildName);
        txtChildZone      = findViewById(R.id.txtChildZone);
        txtCurrentAddress = findViewById(R.id.txtCurrentAddress);
        Button btnConnectChild      = findViewById(R.id.btnConnectChild);
        Button btnRegisterLocation  = findViewById(R.id.btnRegisterLocation);
        Button btnSettings          = findViewById(R.id.btnSettings);
        View mapOverlay             = findViewById(R.id.mapOverlay);

        db = FirebaseDatabase.getInstance(DB_URL).getReference();
        parentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        createNotificationChannel();

        // 미니 맵 초기화
        SupportMapFragment mapFragment = SupportMapFragment.newInstance();
        getSupportFragmentManager().beginTransaction()
                .add(R.id.miniMapContainer, mapFragment)
                .commit();
        mapFragment.getMapAsync(this);

        // 전체화면 지도
        mapOverlay.setOnClickListener(v ->
                startActivity(new Intent(this, ParentMapActivity.class)));

        // 아이 연결하기 → 항상 LinkActivity (추가 연결 가능)
        btnConnectChild.setOnClickListener(v ->
                startActivity(new Intent(this, LinkActivity.class)));

        // 위치 등록하기
        btnRegisterLocation.setOnClickListener(v ->
                startActivity(new Intent(this, LocationRegisterActivity.class)));

        // 설정
        btnSettings.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        loadRegisteredLocations();
        loadParentData();
    }

    // ─────────────────────── 알림 채널 생성 ───────────────────────

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "위치 이탈 알림",
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("아이가 등록된 안전 구역을 벗어날 때 알립니다.");
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(channel);
    }

    // ─────────────────────── 등록된 위치 로드 ───────────────────────

    private void loadRegisteredLocations() {
        registeredLocationsListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                registeredLocations.clear();
                locationNicknames.clear();
                for (DataSnapshot loc : snapshot.getChildren()) {
                    Double lat = loc.child("latitude").getValue(Double.class);
                    Double lng = loc.child("longitude").getValue(Double.class);
                    String nickname = loc.child("nickname").getValue(String.class);
                    if (lat != null && lng != null) {
                        registeredLocations.put(loc.getKey(), new double[]{lat, lng});
                        locationNicknames.put(loc.getKey(),
                                nickname != null ? nickname : "등록 위치");
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {}
        };
        db.child("users").child(parentUid).child("registeredLocations")
                .addValueEventListener(registeredLocationsListener);
    }

    // ─────────────────────── 부모/아이 데이터 로드 ───────────────────────

    private void loadParentData() {
        db.child("users").child(parentUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        String parentName = snapshot.child("name").getValue(String.class);
                        if (parentName != null) txtGreetingName.setText(parentName);

                        DataSnapshot childrenSnap = snapshot.child("children");
                        long childCount = childrenSnap.getChildrenCount();

                        if (childCount == 0) {
                            txtChildName.setText("연결된 아이 없음");
                            txtChildZone.setText("");
                        } else if (childCount == 1) {
                            String childUid = childrenSnap.getChildren().iterator().next().getKey();
                            loadChildAndListen(childUid);
                        } else {
                            txtChildName.setText("아이 " + childCount + "명");
                            txtChildZone.setText("위치 추적 중");
                            for (DataSnapshot child : childrenSnap.getChildren()) {
                                loadChildAndListen(child.getKey());
                            }
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {}
                });
    }

    private void loadChildAndListen(String childUid) {
        db.child("users").child(childUid).child("name")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snap) {
                        String name = snap.getValue(String.class);
                        if (name != null) {
                            childNames.put(childUid, name);
                            if (childNames.size() == 1 && childMarkers.size() <= 1) {
                                txtChildName.setText(name);
                            }
                        }
                        startLocationListener(childUid);
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {}
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

                // 미니 맵 마커 업데이트
                if (miniMap != null) {
                    if (!childMarkers.containsKey(childUid)) {
                        Marker m = miniMap.addMarker(
                                new MarkerOptions().position(position).title(childName));
                        childMarkers.put(childUid, m);
                        miniMap.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 14f));
                    } else {
                        childMarkers.get(childUid).setPosition(position);
                    }
                }

                updateAddress(lat, lng);
                checkGeofence(childUid, childName, lat, lng);
            }

            @Override
            public void onCancelled(DatabaseError error) {}
        });

        locationListeners.put(childUid, listener);
    }

    // ─────────────────────── 지오펜스 확인 ───────────────────────

    /**
     * 아이의 현재 위치와 등록된 모든 안전 구역을 비교한다.
     * 이전에 구역 안에 있었는데 지금 벗어났으면 알림을 발송한다.
     */
    private void checkGeofence(String childUid, String childName,
                                double childLat, double childLng) {
        for (Map.Entry<String, double[]> entry : registeredLocations.entrySet()) {
            String locKey   = entry.getKey();
            double[] coords = entry.getValue();
            String stateKey = childUid + "_" + locKey;
            String locName  = locationNicknames.getOrDefault(locKey, "안전 구역");

            double distance = haversineDistance(childLat, childLng, coords[0], coords[1]);
            boolean inZone  = distance <= GEOFENCE_RADIUS_METERS;

            Boolean prevState = geofenceState.get(stateKey);

            if (prevState == null) {
                // 최초 상태 기록 (알림 없음)
                geofenceState.put(stateKey, inZone);
            } else if (prevState && !inZone) {
                // 구역에서 벗어남 → 알림
                geofenceState.put(stateKey, false);
                sendGeofenceNotification(
                        childName + " 이(가) '" + locName + "' 에서 벗어났습니다.");
            } else if (!prevState && inZone) {
                // 구역에 들어옴 → 상태만 업데이트
                geofenceState.put(stateKey, true);
            }
        }
    }

    /**
     * Haversine 공식으로 두 GPS 좌표 사이 거리(미터)를 계산한다.
     */
    private double haversineDistance(double lat1, double lng1,
                                     double lat2, double lng2) {
        final double R = 6_371_000.0; // 지구 반경 (미터)
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private void sendGeofenceNotification(String message) {
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setContentTitle("PinKid 위치 알림")
                        .setContentText(message)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true);

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(notificationId++, builder.build());
    }

    // ─────────────────────── 주소 역지오코딩 ───────────────────────

    private void updateAddress(double lat, double lng) {
        Geocoder geocoder = new Geocoder(this, Locale.KOREA);
        geocoder.getFromLocation(lat, lng, 1, addresses -> {
            if (addresses != null && !addresses.isEmpty()) {
                String fullAddress = addresses.get(0).getAddressLine(0);
                String zone = addresses.get(0).getThoroughfare() != null
                        ? addresses.get(0).getThoroughfare()
                        : (addresses.get(0).getLocality() != null
                                ? addresses.get(0).getLocality() : "");
                runOnUiThread(() -> {
                    if (fullAddress != null) txtCurrentAddress.setText(fullAddress);
                    if (!zone.isEmpty() && childMarkers.size() == 1)
                        txtChildZone.setText(zone);
                });
            }
        });
    }

    // ─────────────────────── 지도 콜백 ───────────────────────

    @Override
    public void onMapReady(GoogleMap map) {
        miniMap = map;
        miniMap.getUiSettings().setAllGesturesEnabled(false);
        miniMap.getUiSettings().setZoomControlsEnabled(false);
        miniMap.getUiSettings().setMapToolbarEnabled(false);
    }

    // ─────────────────────── 생명주기 ───────────────────────

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 아이 위치 리스너 해제
        for (Map.Entry<String, ValueEventListener> entry : locationListeners.entrySet()) {
            db.child("location").child(entry.getKey()).removeEventListener(entry.getValue());
        }
        // 등록 위치 리스너 해제
        if (registeredLocationsListener != null) {
            db.child("users").child(parentUid).child("registeredLocations")
                    .removeEventListener(registeredLocationsListener);
        }
    }
}
