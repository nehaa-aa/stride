package com.example.app_tracker;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.Priority;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.tasks.Task;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int REQUEST_CHECK_SETTINGS = 2002;
    private static final String TAG = "MainActivity";

    private TextView tvLatitude, tvLongitude, tvAddress;
    private FusedLocationProviderClient fusedLocationClient;
    private DatabaseHelper dbHelper;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private float lastAcceleration, currentAcceleration, shake;
    private Runnable pendingAction;
    private String myUniqueId;

    private final BroadcastReceiver locationUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            double lat = intent.getDoubleExtra("lat", 0);
            double lon = intent.getDoubleExtra("lon", 0);
            updateUI(lat, lon);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvLatitude = findViewById(R.id.tv_latitude);
        tvLongitude = findViewById(R.id.tv_longitude);
        tvAddress = findViewById(R.id.tv_address);
        
        setupUserId();

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        dbHelper = new DatabaseHelper(this);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        findViewById(R.id.btn_get_location).setOnClickListener(v -> checkSettingsAndExecute(this::getCurrentLocation));
        findViewById(R.id.btn_start_tracking).setOnClickListener(v -> checkSettingsAndExecute(this::startTrackingService));
        findViewById(R.id.btn_stop_tracking).setOnClickListener(v -> {
            stopService(new Intent(this, LocationService.class));
            Toast.makeText(this, "Tracking Stopped", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btn_open_map).setOnClickListener(v -> checkSettingsAndExecute(() -> {
            Intent intent = new Intent(this, MapActivity.class);
            startActivity(intent);
        }));

        findViewById(R.id.btn_share_location).setOnClickListener(v -> showLocationActionDialog());
        findViewById(R.id.btn_view_history).setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));
    }

    private void setupUserId() {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        myUniqueId = prefs.getString("user_id", null);
        if (myUniqueId == null) {
            myUniqueId = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            prefs.edit().putString("user_id", myUniqueId).apply();
        }
    }

    private void showLocationActionDialog() {
        String[] options = {"Share Current Location (Text)", "Share My Tracking ID", "Track a Friend"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Location Actions");
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                shareCurrentLocationText();
            } else if (which == 1) {
                shareTrackingId();
            } else {
                showTrackFriendDialog();
            }
        });
        builder.show();
    }

    private void showTrackFriendDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Track a Friend");
        builder.setMessage("Enter friend's 6-digit ID:");

        final EditText input = new EditText(this);
        input.setHint("e.g. A1B2C3");
        builder.setView(input);

        builder.setPositiveButton("Track Live", null);
        builder.setNegativeButton("Cancel", (dialog, id) -> dialog.cancel());
        
        AlertDialog dialog = builder.create();
        dialog.show();

        Button trackButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        trackButton.setEnabled(false);

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                trackButton.setEnabled(s.toString().trim().length() == 6);
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        trackButton.setOnClickListener(v -> {
            String friendId = input.getText().toString().trim().toUpperCase();
            Intent intent = new Intent(MainActivity.this, MapActivity.class);
            intent.putExtra("FRIEND_ID", friendId);
            startActivity(intent);
            dialog.dismiss();
        });
    }

    private void checkSettingsAndExecute(Runnable action) {
        this.pendingAction = action;
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build();
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest);
        SettingsClient client = LocationServices.getSettingsClient(this);
        Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());

        task.addOnSuccessListener(this, locationSettingsResponse -> checkPermissionsAndExecute(pendingAction));
        task.addOnFailureListener(this, e -> {
            if (e instanceof ResolvableApiException) {
                try {
                    ((ResolvableApiException) e).startResolutionForResult(MainActivity.this, REQUEST_CHECK_SETTINGS);
                } catch (IntentSender.SendIntentException sendEx) { }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CHECK_SETTINGS) {
            if (resultCode == RESULT_OK && pendingAction != null) {
                checkPermissionsAndExecute(pendingAction);
            } else {
                Toast.makeText(this, "Location must be enabled", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startTrackingService() {
        Intent serviceIntent = new Intent(this, LocationService.class);
        serviceIntent.putExtra("USER_ID", myUniqueId);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        Toast.makeText(this, "Tracking Started. ID: " + myUniqueId, Toast.LENGTH_LONG).show();
    }

    private void shareTrackingId() {
        String msg = "Track my live location in App Tracker! My ID is: " + myUniqueId;
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, msg);
        startActivity(Intent.createChooser(intent, "Share Tracking ID"));
    }

    private void shareCurrentLocationText() {
        String msg = "My current location:\n" + tvAddress.getText() +
                     "\n" + tvLatitude.getText() +
                     "\n" + tvLongitude.getText() +
                     "\nShared via App Tracker";
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, msg);
        startActivity(Intent.createChooser(intent, "Share Current Location"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }
        IntentFilter filter = new IntentFilter("LocationUpdates");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(locationUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(locationUpdateReceiver, filter);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) sensorManager.unregisterListener(this);
        try { unregisterReceiver(locationUpdateReceiver); } catch (Exception ignored) {}
    }

    private void checkPermissionsAndExecute(Runnable action) {
        List<String> permissions = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && 
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        } else {
            if (action != null) action.run();
        }
    }

    private void getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        CancellationTokenSource cts = new CancellationTokenSource();
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.getToken())
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        updateUI(location.getLatitude(), location.getLongitude());
                        dbHelper.insertLocation(location.getLatitude(), location.getLongitude(), getAddress(location.getLatitude(), location.getLongitude()));
                    } else {
                        Toast.makeText(this, "Location unavailable", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to get location", e));
    }

    private void updateUI(double lat, double lon) {
        tvLatitude.setText("Latitude: " + lat);
        tvLongitude.setText("Longitude: " + lon);
        new Thread(() -> {
            String addr = getAddress(lat, lon);
            runOnUiThread(() -> tvAddress.setText("Address: " + addr));
        }).start();
    }

    private String getAddress(double lat, double lon) {
        try {
            List<Address> addresses = new Geocoder(this, Locale.getDefault()).getFromLocation(lat, lon, 1);
            if (addresses != null && !addresses.isEmpty()) return addresses.get(0).getAddressLine(0);
        } catch (IOException e) { Log.e(TAG, "Geocoder error", e); }
        return "Address unavailable";
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float x = event.values[0], y = event.values[1], z = event.values[2];
        lastAcceleration = currentAcceleration;
        currentAcceleration = (float) Math.sqrt(x*x + y*y + z*z);
        shake = shake * 0.9f + (currentAcceleration - lastAcceleration);
        if (shake > 15) checkSettingsAndExecute(this::getCurrentLocation);
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted && pendingAction != null) {
                pendingAction.run();
            }
        }
    }
}