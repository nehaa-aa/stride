package com.example.app_tracker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class LocationService extends Service {

    private static final String CHANNEL_ID = "LocationServiceChannel";
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private DatabaseHelper dbHelper;
    private DatabaseReference firebaseRef;
    private String userId;

    @Override
    public void onCreate() {
        super.onCreate();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        dbHelper = new DatabaseHelper(this);
        firebaseRef = FirebaseDatabase.getInstance().getReference("live_locations");
        createNotificationChannel();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;
                for (Location location : locationResult.getLocations()) {
                    // 1. Save locally
                    dbHelper.insertLocation(location.getLatitude(), location.getLongitude(), "Auto Tracked");
                    
                    // 2. Upload to Firebase for remote tracking
                    if (userId != null) {
                        Map<String, Object> locData = new HashMap<>();
                        locData.put("lat", location.getLatitude());
                        locData.put("lon", location.getLongitude());
                        locData.put("timestamp", System.currentTimeMillis());
                        firebaseRef.child(userId).setValue(locData);
                    }

                    // 3. Broadcast for MainActivity UI
                    Intent intent = new Intent("LocationUpdates");
                    intent.putExtra("lat", location.getLatitude());
                    intent.putExtra("lon", location.getLongitude());
                    sendBroadcast(intent);
                }
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            userId = intent.getStringExtra("USER_ID");
        }

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("GPS Tracker Active")
                .setContentText("Your ID: " + (userId != null ? userId : "Generating..."))
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentIntent(pendingIntent)
                .build();

        startForeground(1, notification);
        startLocationUpdates();

        return START_STICKY;
    }

    private void startLocationUpdates() {
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMinUpdateIntervalMillis(3000)
                .build();

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest,
                    locationCallback, Looper.getMainLooper());
        } catch (SecurityException e) {
            Log.e("LocationService", "Lost location permission." + e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Location Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }
}