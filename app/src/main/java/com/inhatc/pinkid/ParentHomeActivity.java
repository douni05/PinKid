package com.inhatc.pinkid;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Geocoder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ParentHomeActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String DB_URL                = "https://pinkid-1fec4-default-rtdb.asia-southeast1.firebasedatabase.app";
    private static final String CHANNEL_ID            = "pinkid_geofence";
    private static final String SOS_CHANNEL_ID        = "pinkid_sos";
    private static final double GEOFENCE_RADIUS_METERS = 300.0;
    private static final long   LOCATION_TIMEOUT_MS   = 5 * 60 * 1000L; // 5분

    // 아이 마커 색상 순환 목록
    private static final float[] MARKER_HUES = {
            BitmapDescriptorFactory.HUE_GREEN,
            BitmapDescriptorFactory.HUE_AZURE,
            BitmapDescriptorFactory.HUE_ORANGE,
            BitmapDescriptorFactory.HUE_VIOLET,
            BitmapDescriptorFactory.HUE_ROSE
    };

    private TextView txtGreetingName, txtChildName, txtChildZone, txtCurrentAddress;
    private TextView txtLastUpdate, txtMapHint;
    private DatabaseReference db;
    private String parentUid;

    private GoogleMap miniMap;
    private final Map<String, Marker>             childMarkers      = new HashMap<>();
    private final Map<String, ValueEventListener> locationListeners = new HashMap<>();
    private final Map<String, String>             childNames        = new HashMap<>();
    private final Map<String, Float>              childHues         = new HashMap<>();
    private int hueIndex = 0;

    // 지오펜스
    private final Map<String, double[]>  registeredLocations = new HashMap<>();
    private final Map<String, String>    locationNicknames   = new HashMap<>();
    private final Map<String, Boolean>   geofenceState       = new HashMap<>();
    private ValueEventListener registeredLocationsListener;
    private int notificationId = 2000;

    // SOS
    private long sosListenStartTime;
    private final Map<String, ValueEventListener> sosListeners = new HashMap<>();

    // 위치 미수신 타임아웃
    private final Handler               timeoutHandler   = new Handler(Looper.getMainLooper());
    private final Map<String, Runnable> timeoutRunnables = new HashMap<>();

    // 알림 권한 요청 런처
    private ActivityResultLauncher<String> notifPermLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ActivityResultLauncher는 항상 onCreate 초반에 등록해야 함
        notifPermLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> { /* no-op */ });

        setContentView(R.layout.activity_parent_home);

        // ── null 체크 ──
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        txtGreetingName   = findViewById(R.id.txtGreetingName);
        txtChildName      = findViewById(R.id.txtChildName);
        txtChildZone      = findViewById(R.id.txtChildZone);
        txtCurrentAddress = findViewById(R.id.txtCurrentAddress);
        txtLastUpdate     = findViewById(R.id.txtLastUpdate);
        txtMapHint        = findViewById(R.id.txtMapHint);
        Button btnConnectChild     = findViewById(R.id.btnConnectChild);
        Button btnRegisterLocation = findViewById(R.id.btnRegisterLocation);
        Button btnSettings         = findViewById(R.id.btnSettings);
        View   mapOverlay          = findViewById(R.id.mapOverlay);

        db        = FirebaseDatabase.getInstance(DB_URL).getReference();
        parentUid = currentUser.getUid();

        // 알림 런타임 권한 요청 (Android 13+ 필수)
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
        }

        createNotificationChannel();

        // 미니 맵 초기화
        SupportMapFragment mapFragment = SupportMapFragment.newInstance();
        getSupportFragmentManager().beginTransaction()
                .add(R.id.miniMapContainer, mapFragment)
                .commit();
        mapFragment.getMapAsync(this);

        mapOverlay.setOnClickListener(v ->
                startActivity(new Intent(this, ParentMapActivity.class)));
        btnConnectChild.setOnClickListener(v ->
                startActivity(new Intent(this, LinkActivity.class)));
        btnRegisterLocation.setOnClickListener(v ->
                startActivity(new Intent(this, LocationRegisterActivity.class)));
        btnSettings.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        loadRegisteredLocations();
        loadParentData();
    }

    // ─────────────────────── 알림 채널 ───────────────────────

    private void createNotificationChannel() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;

        // 지오펜스 채널
        NotificationChannel geofenceChannel = new NotificationChannel(
                CHANNEL_ID, "위치 알림", NotificationManager.IMPORTANCE_HIGH);
        geofenceChannel.setDescription("아이의 안전 구역 이탈·도착 및 위치 미수신 알림");
        nm.createNotificationChannel(geofenceChannel);

        // SOS 채널 (최우선)
        NotificationChannel sosChannel = new NotificationChannel(
                SOS_CHANNEL_ID, "SOS 긴급 알림", NotificationManager.IMPORTANCE_MAX);
        sosChannel.setDescription("아이가 SOS를 요청할 때 즉시 알립니다.");
        sosChannel.enableVibration(true);
        nm.createNotificationChannel(sosChannel);
    }

    // ─────────────────────── 등록 위치 로드 ───────────────────────

    private void loadRegisteredLocations() {
        registeredLocationsListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                registeredLocations.clear();
                locationNicknames.clear();
                for (DataSnapshot loc : snapshot.getChildren()) {
                    Double lat      = loc.child("latitude").getValue(Double.class);
                    Double lng      = loc.child("longitude").getValue(Double.class);
                    String nickname = loc.child("nickname").getValue(String.class);
                    if (lat != null && lng != null) {
                        registeredLocations.put(loc.getKey(), new double[]{lat, lng});
                        locationNicknames.put(loc.getKey(),
                                nickname != null ? nickname : "등록 위치");
                    }
                }
            }
            @Override
            public void onCancelled(DatabaseError error) {
                android.util.Log.w("PinKid", "registeredLocations load failed: " + error.getMessage());
            }
        };
        db.child("users").child(parentUid).child("registeredLocations")
                .addValueEventListener(registeredLocationsListener);
    }

    // ─────────────────────── 부모/아이 데이터 로드 ───────────────────────

    private void loadParentData() {
        sosListenStartTime = System.currentTimeMillis(); // 이 시각 이후 SOS만 알림
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
                            txtMapHint.setText("연결된 아이가 없습니다\n'아이 연결하기'를 눌러주세요");
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
                    public void onCancelled(DatabaseError error) {
                        runOnUiThread(() ->
                            android.widget.Toast.makeText(ParentHomeActivity.this,
                                "데이터 로드 실패. 네트워크를 확인해 주세요.",
                                android.widget.Toast.LENGTH_SHORT).show());
                    }
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
                        // 아이마다 고유 마커 색상 지정
                        if (!childHues.containsKey(childUid)) {
                            childHues.put(childUid, MARKER_HUES[hueIndex % MARKER_HUES.length]);
                            hueIndex++;
                        }
                        startLocationListener(childUid);
                        startSosListener(childUid);
                    }
                    @Override
                    public void onCancelled(DatabaseError error) {
                        android.util.Log.w("PinKid", "child name load failed: " + error.getMessage());
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

                // 첫 위치 수신 시 안내 문구 숨김
                runOnUiThread(() -> txtMapHint.setVisibility(View.GONE));

                LatLng position  = new LatLng(lat, lng);
                String childName = childNames.getOrDefault(childUid, "아이");
                float  hue       = childHues.getOrDefault(childUid,
                                        BitmapDescriptorFactory.HUE_GREEN);

                // 미니 맵 마커 업데이트
                if (miniMap != null) {
                    if (!childMarkers.containsKey(childUid)) {
                        Marker m = miniMap.addMarker(new MarkerOptions()
                                .position(position)
                                .title(childName)
                                .icon(BitmapDescriptorFactory.defaultMarker(hue)));
                        childMarkers.put(childUid, m);
                        miniMap.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 14f));
                    } else {
                        childMarkers.get(childUid).setPosition(position);
                    }
                }

                // 마지막 업데이트 시간 표시
                Long timestamp = snapshot.child("timestamp").getValue(Long.class);
                if (timestamp != null) updateTimestampText(timestamp);

                updateAddress(lat, lng);
                checkGeofence(childUid, childName, lat, lng);
                resetLocationTimeout(childUid, childName);
            }
            @Override
            public void onCancelled(DatabaseError error) {
                android.util.Log.w("PinKid", "location listener cancelled: " + error.getMessage());
            }
        });
        locationListeners.put(childUid, listener);
    }

    // ─────────────────────── 업데이트 시간 ───────────────────────

    private void updateTimestampText(long timestamp) {
        long diffMs  = System.currentTimeMillis() - timestamp;
        long diffMin = TimeUnit.MILLISECONDS.toMinutes(diffMs);
        String text;
        if      (diffMin < 1)    text = "방금 업데이트";
        else if (diffMin < 60)   text = diffMin + "분 전 업데이트";
        else if (diffMin < 1440) text = TimeUnit.MILLISECONDS.toHours(diffMs) + "시간 전 업데이트";
        else                     text = TimeUnit.MILLISECONDS.toDays(diffMs) + "일 전 업데이트";
        runOnUiThread(() -> txtLastUpdate.setText(text));
    }

    // ─────────────────────── 지오펜스 ───────────────────────

    private void checkGeofence(String childUid, String childName,
                                double childLat, double childLng) {
        for (Map.Entry<String, double[]> entry : registeredLocations.entrySet()) {
            String   locKey   = entry.getKey();
            double[] coords   = entry.getValue();
            String   stateKey = childUid + "_" + locKey;
            String   locName  = locationNicknames.getOrDefault(locKey, "안전 구역");

            double  distance = haversineDistance(childLat, childLng, coords[0], coords[1]);
            boolean inZone   = distance <= GEOFENCE_RADIUS_METERS;
            Boolean prev     = geofenceState.get(stateKey);

            if (prev == null) {
                geofenceState.put(stateKey, inZone);
            } else if (prev && !inZone) {
                geofenceState.put(stateKey, false);
                sendGeofenceNotification(childName + "이(가) '" + locName + "'에서 벗어났습니다.");
            } else if (!prev && inZone) {
                geofenceState.put(stateKey, true);
                sendGeofenceNotification(childName + "이(가) '" + locName + "'에 도착했습니다. ✓");
            }
        }
    }

    private double haversineDistance(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6_371_000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private void sendGeofenceNotification(String message) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("PinKid 위치 알림")
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(notificationId++, builder.build());
    }

    // ─────────────────────── SOS ───────────────────────

    private void startSosListener(String childUid) {
        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Long timestamp = snapshot.child("timestamp").getValue(Long.class);
                // 앱 실행 이후에 생성된 SOS만 알림 (이전 이력 무시)
                if (timestamp != null && timestamp > sosListenStartTime) {
                    String name = childNames.getOrDefault(childUid, "아이");
                    sendSosNotification("🚨 " + name + "이(가) 긴급 도움을 요청했습니다!\n즉시 확인해 주세요.");
                }
            }
            @Override
            public void onCancelled(DatabaseError error) {
                android.util.Log.w("PinKid", "SOS listener cancelled: " + error.getMessage());
            }
        };
        db.child("sos").child(childUid).addValueEventListener(listener);
        sosListeners.put(childUid, listener);
    }

    private void sendSosNotification(String message) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, SOS_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("🚨 긴급 SOS")
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(1000, builder.build()); // 고정 ID → 중복 SOS 시 갱신
    }

    // ─────────────────────── 위치 미수신 타임아웃 ───────────────────────

    private void resetLocationTimeout(String childUid, String childName) {
        Runnable prev = timeoutRunnables.get(childUid);
        if (prev != null) timeoutHandler.removeCallbacks(prev);

        Runnable timeout = () -> sendGeofenceNotification(
                childName + "의 기기에서 5분 이상 위치 정보가 수신되지 않습니다.\n"
                + "배터리 또는 네트워크 상태를 확인해 주세요.");
        timeoutRunnables.put(childUid, timeout);
        timeoutHandler.postDelayed(timeout, LOCATION_TIMEOUT_MS);
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
        // 위치 리스너 해제
        for (Map.Entry<String, ValueEventListener> e : locationListeners.entrySet()) {
            db.child("location").child(e.getKey()).removeEventListener(e.getValue());
        }
        // 등록 위치 리스너 해제
        if (registeredLocationsListener != null && parentUid != null) {
            db.child("users").child(parentUid).child("registeredLocations")
                    .removeEventListener(registeredLocationsListener);
        }
        // SOS 리스너 해제
        for (Map.Entry<String, ValueEventListener> e : sosListeners.entrySet()) {
            db.child("sos").child(e.getKey()).removeEventListener(e.getValue());
        }
        // 타임아웃 핸들러 전체 해제
        timeoutHandler.removeCallbacksAndMessages(null);
    }
}
