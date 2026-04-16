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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

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

public class LocationFragment extends Fragment implements SensorEventListener {

    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int REQUEST_CHECK_SETTINGS = 2002;
    private static final String TAG = "LocationFragment";

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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_location, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvLatitude = view.findViewById(R.id.tv_latitude);
        tvLongitude = view.findViewById(R.id.tv_longitude);
        tvAddress = view.findViewById(R.id.tv_address);

        setupUserId();

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        dbHelper = new DatabaseHelper(requireContext());

        sensorManager = (SensorManager) requireContext().getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        view.findViewById(R.id.btn_get_location).setOnClickListener(v ->
                checkSettingsAndExecute(this::getCurrentLocation));
        view.findViewById(R.id.btn_start_tracking).setOnClickListener(v ->
                checkSettingsAndExecute(this::startTrackingService));
        view.findViewById(R.id.btn_stop_tracking).setOnClickListener(v -> {
            requireActivity().stopService(new Intent(requireContext(), LocationService.class));
            Toast.makeText(requireContext(), "Tracking Stopped", Toast.LENGTH_SHORT).show();
        });
        view.findViewById(R.id.btn_open_map).setOnClickListener(v ->
                checkSettingsAndExecute(() -> {
                    Intent intent = new Intent(requireContext(), MapActivity.class);
                    startActivity(intent);
                }));
        view.findViewById(R.id.btn_share_location).setOnClickListener(v ->
                showLocationActionDialog());
    }

    private void setupUserId() {
        SharedPreferences prefs = requireActivity().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        myUniqueId = prefs.getString("user_id", null);
        if (myUniqueId == null) {
            myUniqueId = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            prefs.edit().putString("user_id", myUniqueId).apply();
        }
    }

    private void showLocationActionDialog() {
        String[] options = {"Share Current Location (Text)", "Share My Tracking ID"};
        new AlertDialog.Builder(requireContext())
                .setTitle("Location Actions")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) shareCurrentLocationText();
                    else if (which == 1) shareTrackingId();
                }).show();
    }
    private void showTrackFriendDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Track a Friend");
        builder.setMessage("Enter friend's 6-digit ID:");
        final EditText input = new EditText(requireContext());
        input.setHint("e.g. A1B2C3");
        builder.setView(input);
        builder.setPositiveButton("Track Live", null);
        builder.setNegativeButton("Cancel", (dialog, id) -> dialog.cancel());
        AlertDialog dialog = builder.create();
        dialog.show();

        Button trackButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        trackButton.setEnabled(false);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                trackButton.setEnabled(s.toString().trim().length() == 6);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        trackButton.setOnClickListener(v -> {
            String friendId = input.getText().toString().trim().toUpperCase();
            Intent intent = new Intent(requireContext(), MapActivity.class);
            intent.putExtra("FRIEND_ID", friendId);
            startActivity(intent);
            dialog.dismiss();
        });
    }

    private void checkSettingsAndExecute(Runnable action) {
        this.pendingAction = action;
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build();
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest);
        SettingsClient client = LocationServices.getSettingsClient(requireActivity());
        Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());
        task.addOnSuccessListener(requireActivity(), r -> checkPermissionsAndExecute(pendingAction));
        task.addOnFailureListener(requireActivity(), e -> {
            if (e instanceof ResolvableApiException) {
                try {
                    ((ResolvableApiException) e).startResolutionForResult(requireActivity(), REQUEST_CHECK_SETTINGS);
                } catch (IntentSender.SendIntentException ignored) {}
            }
        });
    }

    private void startTrackingService() {
        Intent serviceIntent = new Intent(requireContext(), LocationService.class);
        serviceIntent.putExtra("USER_ID", myUniqueId);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireActivity().startForegroundService(serviceIntent);
        } else {
            requireActivity().startService(serviceIntent);
        }
        Toast.makeText(requireContext(), "Tracking Started. ID: " + myUniqueId, Toast.LENGTH_LONG).show();
    }

    private void shareTrackingId() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, "Track my live location! My ID is: " + myUniqueId);
        startActivity(Intent.createChooser(intent, "Share Tracking ID"));
    }

    private void shareCurrentLocationText() {
        String msg = "My current location:\n" + tvAddress.getText() +
                "\n" + tvLatitude.getText() + "\n" + tvLongitude.getText() +
                "\nShared via Stride";
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, msg);
        startActivity(Intent.createChooser(intent, "Share Current Location"));
    }

    private void checkPermissionsAndExecute(Runnable action) {
        List<String> permissions = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions(requireActivity(), permissions.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        } else {
            if (action != null) action.run();
        }
    }

    private void getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;
        CancellationTokenSource cts = new CancellationTokenSource();
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.getToken())
                .addOnSuccessListener(requireActivity(), location -> {
                    if (location != null) {
                        updateUI(location.getLatitude(), location.getLongitude());
                        dbHelper.insertLocation(location.getLatitude(), location.getLongitude(),
                                getAddress(location.getLatitude(), location.getLongitude()));
                    } else {
                        Toast.makeText(requireContext(), "Location unavailable", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to get location", e));
    }

    private void updateUI(double lat, double lon) {
        tvLatitude.setText(String.valueOf(lat));
        tvLongitude.setText(String.valueOf(lon));
        new Thread(() -> {
            String addr = getAddress(lat, lon);
            if (getActivity() != null) requireActivity().runOnUiThread(() -> tvAddress.setText(addr));
        }).start();
    }

    private String getAddress(double lat, double lon) {
        try {
            List<Address> addresses = new Geocoder(requireContext(), Locale.getDefault()).getFromLocation(lat, lon, 1);
            if (addresses != null && !addresses.isEmpty()) return addresses.get(0).getAddressLine(0);
        } catch (IOException e) { Log.e(TAG, "Geocoder error", e); }
        return "Address unavailable";
    }

    @Override
    public void onResume() {
        super.onResume();
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }
        IntentFilter filter = new IntentFilter("LocationUpdates");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(locationUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            requireContext().registerReceiver(locationUpdateReceiver, filter);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (sensorManager != null) sensorManager.unregisterListener(this);
        try { requireContext().unregisterReceiver(locationUpdateReceiver); } catch (Exception ignored) {}
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float x = event.values[0], y = event.values[1], z = event.values[2];
        lastAcceleration = currentAcceleration;
        currentAcceleration = (float) Math.sqrt(x * x + y * y + z * z);
        shake = shake * 0.9f + (currentAcceleration - lastAcceleration);
        if (shake > 15) checkSettingsAndExecute(this::getCurrentLocation);
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}