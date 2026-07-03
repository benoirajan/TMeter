package com.tmeter;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.tmeter.db.AppDatabase;
import com.tmeter.db.TemperatureLog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LogAnalyticsActivity extends AppCompatActivity {

    private Button btnFromDate, btnToDate, btnApplyFilter;
    private TextView tvLogCount;
    private RecyclerView rvLogs;
    private AppDatabase database;
    private LogAdapter adapter;

    private Long filterFrom = null;
    private Long filterTo = null;

    private final SimpleDateFormat displayDateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log_analytics);

        btnFromDate = findViewById(R.id.btnFromDate);
        btnToDate = findViewById(R.id.btnToDate);
        btnApplyFilter = findViewById(R.id.btnApplyFilter);
        tvLogCount = findViewById(R.id.tvLogCount);
        rvLogs = findViewById(R.id.rvLogs);
        ImageButton btnBack = findViewById(R.id.btnBack);

        database = AppDatabase.getDatabase(this);

        adapter = new LogAdapter();
        rvLogs.setLayoutManager(new LinearLayoutManager(this));
        rvLogs.setAdapter(adapter);
        rvLogs.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));

        btnBack.setOnClickListener(v -> finish());
        btnFromDate.setOnClickListener(v -> showDatePicker(true));
        btnToDate.setOnClickListener(v -> showDatePicker(false));
        btnApplyFilter.setOnClickListener(v -> loadLogs());

        loadLogs();
    }

    private void showDatePicker(boolean isFromDate) {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            Calendar selected = Calendar.getInstance();
            selected.set(year, month, dayOfMonth);

            if (isFromDate) {
                selected.set(Calendar.HOUR_OF_DAY, 0);
                selected.set(Calendar.MINUTE, 0);
                selected.set(Calendar.SECOND, 0);
                selected.set(Calendar.MILLISECOND, 0);
                filterFrom = selected.getTimeInMillis();
                btnFromDate.setText(displayDateFormat.format(selected.getTime()));
            } else {
                selected.set(Calendar.HOUR_OF_DAY, 23);
                selected.set(Calendar.MINUTE, 59);
                selected.set(Calendar.SECOND, 59);
                selected.set(Calendar.MILLISECOND, 999);
                filterTo = selected.getTimeInMillis();
                btnToDate.setText(displayDateFormat.format(selected.getTime()));
            }
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void loadLogs() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<TemperatureLog> logs;

            if (filterFrom != null && filterTo != null) {
                logs = database.temperatureLogDao().getLogsBetween(filterFrom, filterTo);
            } else if (filterFrom != null) {
                logs = database.temperatureLogDao().getLogsAfter(filterFrom);
            } else if (filterTo != null) {
                logs = database.temperatureLogDao().getLogsBefore(filterTo);
            } else {
                logs = database.temperatureLogDao().getAllLogsDirect();
            }

            runOnUiThread(() -> {
                adapter.setData(logs);
                tvLogCount.setText(String.format(Locale.getDefault(), "Showing %d logs", logs.size()));
            });
        });
    }

    // RecyclerView Adapter
    static class LogAdapter extends RecyclerView.Adapter<LogAdapter.LogViewHolder> {

        private List<TemperatureLog> data = new ArrayList<>();
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

        void setData(List<TemperatureLog> logs) {
            this.data = logs;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_log_row, parent, false);
            return new LogViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
            TemperatureLog log = data.get(position);
            Date date = new Date(log.getTimestamp());
            holder.tvDate.setText(dateFormat.format(date));
            holder.tvTime.setText(timeFormat.format(date));
            holder.tvTemp.setText(String.format(Locale.getDefault(), "%.1f", log.getTemperature()));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        static class LogViewHolder extends RecyclerView.ViewHolder {
            TextView tvDate, tvTime, tvTemp;

            LogViewHolder(@NonNull View itemView) {
                super(itemView);
                tvDate = itemView.findViewById(R.id.tvDate);
                tvTime = itemView.findViewById(R.id.tvTime);
                tvTemp = itemView.findViewById(R.id.tvTemp);
            }
        }
    }
}
