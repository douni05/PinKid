package com.inhatc.pinkid;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Geocoder;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
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

import java.util.Locale;

public class ChildActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String DB_URL = "https://pinkid-1fec4-default-rtdb.asia-southeast1.firebasedatabase.app";

    private TextView txtChildName, txtChildZone, txtCurrentAddress;
    private GoogleMap miniMap;
    private Marker selfMarker;
    private FusedLocationProviderClient fusedClient;
    private LocationCallback locationCallback;

    private DatabaseReference db;
    private String childUid;
    private String childName = "나";

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean fine = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                if (Boolean.TRUE.equals(fine)) {
                    startTrackingService();
                    startLocationUpdates();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_child);

        txtChildName      = findViewById(R.id.txtChildName);
        txtChildZone      = findViewById(R.id.txtChildZone);
        txtCurrentAddress = findViewById(R.id.txtCurrentAddress);
        Button btnSettings = findViewById(R.id.btnSettings);

        btnSettings.setOnClickListener(v ->
                startActivity(new Intent(this, ChildSettingsActivity.class)));

        db = FirebaseDatabase.getInstance(DB_URL).getReference();
        fusedClient = LocationServices.getFusedLocationProviderClient(this);

        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) { finish(); return; }
        childUid = auth.getCurrentUser().getUid();

        // 아이 이름 로드
        loadChildName();

        // 미니맵 초기화
        SupportMapFragment mapFragment = SupportMapFragment.newInstance();
        getSupportFragmentManager().beginTransaction()
                .add(R.id.miniMapContainer, mapFragment)
                .commit();
        mapFragment.getMapAsync(this);

        // 위치 콜백 정의
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                if (result == null) return;
                android.location.Location loc = result.getLastLocation();
                if (loc == null) return;

                LatLng pos = new LatLng(loc.getLatitude(), loc.getLongitude());
                updateMap(pos);
                updateAddress(loc.getLatitude(), loc.getLongitude());
            }
        };

        // 권한 요청 후 시작
        requestPermissionsAndStart();
    }

    private void loadChildName() {
        db.child("users").child(childUid).child("name")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        String name = snapshot.getValue(String.class);
                        if (name != null) {
                            childName = name;
                            txtChildName.setText(name);
                            if (selfMarker != null) selfMarker.setTitle(name);
                        }
                    }
                    @Override
                    public void onCancelled(DatabaseError error) {}
                });
    }

    private void requestPermissionsAndStart() {
        boolean hasFine = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (hasFine) {
            startTrackingService();
            startLocationUpdates();
        } else {
            permissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.POST_NOTIFICATIONS
            });
        }
    }

    private void startTrackingService() {
        ContextCompat.startForegroundService(this, new Intent(this, LocationService.class));
    }

    @SuppressWarnings("MissingPermission")
    private void startLocationUpdates() {
        LocationRequest request = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 10_000L)
                .setMinUpdateIntervalMillis(5_000L)
                .build();
        fusedClient.requestLocationUpdates(request, locationCallback, getMainLooper());
    }

    @Override
    public void onMapReady(GoogleMap map) {
        miniMap = map;
        miniMap.getUiSettings().setAllGesturesEnabled(false);
        miniMap.getUiSettings().setZoomControlsEnabled(false);
        miniMap.getUiSettings().setMapToolbarEnabled(false);
    }

    private void updateMap(LatLng pos) {
        if (miniMap == null) return;
        if (selfMarker == null) {
            selfMarker = miniMap.addMarker(
                    new MarkerOptions().position(pos).title(childName));
            miniMap.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 15f));
        } else {
            selfMarker.setPosition(pos);
            miniMap.animateCamera(CameraUpdateFactory.newLatLng(pos));
        }
    }

    private void updateAddress(double lat, double lng) {
        Geocoder geocoder = new Geocoder(this, Locale.KOREA);
        geocoder.getFromLocation(lat, lng, 1, addresses -> {
            if (addresses == null || addresses.isEmpty()) return;
            String fullAddress = addresses.get(0).getAddressLine(0);
            String zone = addresses.get(0).getThoroughfare() != null
                    ? addresses.get(0).getThoroughfare()
                    : (addresses.get(0).getLocality() != null
                        ? addresses.get(0).getLocality() : "");
            runOnUiThread(() -> {
                if (fullAddress != null) txtCurrentAddress.setText(fullAddress);
                if (!zone.isEmpty()) txtChildZone.setText(zone);
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (fusedClient != null && locationCallback != null) {
            fusedClient.removeLocationUpdates(locationCallback);
        }
    }
}
