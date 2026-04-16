package com.example.app_tracker;

import android.database.Cursor;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

public class HistoryActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private ListView lvHistory;
    private Button btnClear;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        // Add Back Button
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Location History");
        }

        dbHelper = new DatabaseHelper(this);
        lvHistory = findViewById(R.id.lv_history);
        btnClear = findViewById(R.id.btn_clear_history);

        loadHistory();

        btnClear.setOnClickListener(v -> {
            dbHelper.deleteHistory();
            loadHistory();
            Toast.makeText(this, "History Cleared", Toast.LENGTH_SHORT).show();
        });
    }

    private void loadHistory() {
        Cursor cursor = dbHelper.getAllHistory();
        
        SimpleCursorAdapter adapter = new SimpleCursorAdapter(
                this,
                android.R.layout.simple_list_item_2,
                cursor,
                new String[]{DatabaseHelper.COL_TIMESTAMP, DatabaseHelper.COL_LATITUDE},
                new int[]{android.R.id.text1, android.R.id.text2},
                0
        );
        
        adapter.setViewBinder((view, cursor1, columnIndex) -> {
            if (columnIndex == cursor1.getColumnIndex(DatabaseHelper.COL_LATITUDE)) {
                double lat = cursor1.getDouble(columnIndex);
                double lon = cursor1.getDouble(cursor1.getColumnIndex(DatabaseHelper.COL_LONGITUDE));
                String address = cursor1.getString(cursor1.getColumnIndex(DatabaseHelper.COL_ADDRESS));
                ((android.widget.TextView) view).setText("Lat: " + lat + ", Lon: " + lon + "\n" + address);
                return true;
            }
            return false;
        });

        lvHistory.setAdapter(adapter);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}