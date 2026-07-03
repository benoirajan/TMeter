package com.tmeter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CalendarView;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
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

public class RecordingCalendarActivity extends AppCompatActivity {

    private CalendarView calendarView;
    private TextView tvSelectedDate, tvDaySummary;
    private RecyclerView rvDayLogs;
    private AppDatabase database;
    private DayLogAdapter adapter;

    private final SimpleDateFormat displayFormat = new SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recording_calendar);

        calendarView = findViewById(R.id.calendarView);
        tvSelectedDate = findViewById(R.id.tvSelectedDate);
        tvDaySummary = findViewById(R.id.tvDaySummary);
        rvDayLogs = findViewById(R.id.rvDayLogs);
        ImageButton btnBack = findViewById(R.id.btnBack);

        database = AppDatabase.getDatabase(this);
        adapter = new DayLogAdapter();
        rvDayLogs.setLayoutManager(new LinearLayoutManager(this));
        rvDayLogs.setAdapter(adapter);

        btnBack.setOnClickListener(v -> finish());

        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            Calendar selected = Calendar.getInstance();
            selected.set(year, month, dayOfMonth, 0, 0, 0);
            selected.set(Calendar.MILLISECOND, 0);
            loadLogsForDay(selected);
        });

        // Load today's data initially
        loadLogsForDay(Calendar.getInstance());
    }

    private void loadLogsForDay(Calendar day) {
        Calendar startOfDay = (Calendar) day.clone();
        startOfDay.set(Calendar.HOUR_OF_DAY, 0);
        startOfDay.set(Calendar.MINUTE, 0);
        startOfDay.set(Calendar.SECOND, 0);
        startOfDay.set(Calendar.MILLISECOND, 0);

        long from = startOfDay.getTimeInMillis();
        long to = from + 86400000L - 1; // end of day

        tvSelectedDate.setText(displayFormat.format(new Date(from)));

        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<TemperatureLog> logs = database.temperatureLogDao().getLogsBetween(from, to);

            runOnUiThread(() -> {
                if (logs.isEmpty()) {
                    tvDaySummary.setText("No recordings on this day");
                    adapter.setData(logs);
                } else {
                    float min = Float.MAX_VALUE, max = Float.MIN_VALUE, sum = 0;
                    for (TemperatureLog log : logs) {
                        float t = log.getTemperature();
                        if (t < min) min = t;
                        if (t > max) max = t;
                        sum += t;
                    }
                    float avg = sum / logs.size();
                    tvDaySummary.setText(String.format(Locale.getDefault(),
                            "%d readings  •  Min: %.1f°C  •  Max: %.1f°C  •  Avg: %.1f°C",
                            logs.size(), min, max, avg));
                    adapter.setData(logs);
                }
            });
        });
    }

    // Simple adapter showing time + temp for the selected day
    static class DayLogAdapter extends RecyclerView.Adapter<DayLogAdapter.VH> {

        private List<TemperatureLog> data = new ArrayList<>();
        private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

        void setData(List<TemperatureLog> logs) {
            this.data = logs;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_log_row, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            TemperatureLog log = data.get(position);
            Date date = new Date(log.getTimestamp());
            holder.tvDate.setText(String.valueOf(position + 1));
            holder.tvTime.setText(timeFormat.format(date));
            holder.tvTemp.setText(String.format(Locale.getDefault(), "%.1f°C", log.getTemperature()));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvDate, tvTime, tvTemp;

            VH(@NonNull View itemView) {
                super(itemView);
                tvDate = itemView.findViewById(R.id.tvDate);
                tvTime = itemView.findViewById(R.id.tvTime);
                tvTemp = itemView.findViewById(R.id.tvTemp);
            }
        }
    }
}
