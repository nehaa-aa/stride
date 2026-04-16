package com.example.app_tracker;

import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;

public class HistoryFragment extends Fragment {

    private DatabaseHelper dbHelper;
    private ListView lvHistory;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        dbHelper = new DatabaseHelper(requireContext());
        lvHistory = view.findViewById(R.id.lv_history);
        MaterialButton btnClear = view.findViewById(R.id.btn_clear_history);

        loadHistory();

        btnClear.setOnClickListener(v -> {
            dbHelper.deleteHistory();
            loadHistory();
            Toast.makeText(requireContext(), "History Cleared", Toast.LENGTH_SHORT).show();
        });
    }

    private void loadHistory() {
        Cursor cursor = dbHelper.getAllHistory();

        HistoryCursorAdapter adapter = new HistoryCursorAdapter(requireContext(), cursor);
        lvHistory.setAdapter(adapter);
    }
}