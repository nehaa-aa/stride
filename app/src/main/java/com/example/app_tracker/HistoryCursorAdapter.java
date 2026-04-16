package com.example.app_tracker;

import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.TextView;

public class HistoryCursorAdapter extends CursorAdapter {

    public HistoryCursorAdapter(Context context, Cursor cursor) {
        super(context, cursor, 0);
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return LayoutInflater.from(context).inflate(R.layout.item_history, parent, false);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        TextView tvTimestamp = view.findViewById(R.id.tv_hist_timestamp);
        TextView tvCoords = view.findViewById(R.id.tv_hist_coords);
        TextView tvAddress = view.findViewById(R.id.tv_hist_address);

        String timestamp = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TIMESTAMP));
        double lat = cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_LATITUDE));
        double lon = cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_LONGITUDE));
        String address = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ADDRESS));

        tvTimestamp.setText(timestamp);
        tvCoords.setText(lat + ", " + lon);
        tvAddress.setText(address != null ? address : "");
    }
}