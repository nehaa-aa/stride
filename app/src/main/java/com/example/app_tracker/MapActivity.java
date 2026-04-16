package com.example.app_tracker;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

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
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.maps.android.heatmaps.HeatmapTileProvider;

import java.util.ArrayList;
import java.util.List;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final int MAP_LOCATION_REQUEST = 2001;

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private Marker currentMarker;
    private Polyline trailLine;
    private List<LatLng> pathPoints = new ArrayList<>();
    private boolean cameraMoved = false;

    private List<LatLng> heatmapPoints = new ArrayList<>();
    private TileOverlay heatmapOverlay;
    
    private String friendId;
    private DatabaseReference friendRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        friendId = getIntent().getStringExtra("FRIEND_ID");

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(friendId != null ? "Tracking: " + friendId : "Advanced Map View");
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                if (friendId == null) { // Only track self if not tracking a friend
                    Location location = locationResult.getLastLocation();
                    if (location != null) updateMapLocation(location.getLatitude(), location.getLongitude());
                }
            }
        };

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);

        trailLine = mMap.addPolyline(new PolylineOptions().color(Color.BLUE).width(10).geodesic(true));

        if (friendId != null) {
            startFriendTracking();
        } else {
            setupMap();
        }
    }

    private void startFriendTracking() {
        friendRef = FirebaseDatabase.getInstance().getReference("live_locations").child(friendId);
        friendRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Double lat = snapshot.child("lat").getValue(Double.class);
                    Double lon = snapshot.child("lon").getValue(Double.class);
                    if (lat != null && lon != null) {
                        updateMapLocation(lat, lon);
                    }
                } else {
                    Toast.makeText(MapActivity.this, "Friend ID not found or offline", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    private void setupMap() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MAP_LOCATION_REQUEST);
            return;
        }
        mMap.setMyLocationEnabled(true);
        startLocationUpdates();
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000).build();
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    private void updateMapLocation(double lat, double lon) {
        if (mMap == null) return;
        LatLng current = new LatLng(lat, lon);
        pathPoints.add(current);
        heatmapPoints.add(current);

        trailLine.setPoints(pathPoints);

        if (currentMarker == null) {
            currentMarker = mMap.addMarker(new MarkerOptions()
                    .position(current)
                    .title(friendId != null ? "Friend" : "Me")
                    .icon(BitmapDescriptorFactory.defaultMarker(friendId != null ? BitmapDescriptorFactory.HUE_RED : BitmapDescriptorFactory.HUE_AZURE)));
        } else {
            currentMarker.setPosition(current);
        }

        if (heatmapPoints.size() % 5 == 0) updateHeatmap();

        if (!cameraMoved) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(current, 17f));
            cameraMoved = true;
        }
    }

    private void updateHeatmap() {
        if (heatmapPoints.size() < 2) return;
        HeatmapTileProvider provider = new HeatmapTileProvider.Builder().data(heatmapPoints).radius(50).build();
        if (heatmapOverlay != null) heatmapOverlay.remove();
        heatmapOverlay = mMap.addTileOverlay(new TileOverlayOptions().tileProvider(provider));
    }

    @Override
    protected void onPause() {
        super.onPause();
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}