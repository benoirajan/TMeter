package com.tmeter.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface TemperatureLogDao {
    
    @Insert
    void insert(TemperatureLog log);

    @Query("SELECT * FROM (SELECT * FROM temperature_logs WHERE timestamp >= :startOfDay ORDER BY timestamp DESC LIMIT 500) ORDER BY timestamp ASC")
    LiveData<List<TemperatureLog>> getTodayLogsLive(long startOfDay);

    @Query("SELECT * FROM temperature_logs ORDER BY timestamp DESC LIMIT 500")
    List<TemperatureLog> getRecentLogsDirect();

    @Query("DELETE FROM temperature_logs")
    void clearAllLogs();

    @Query("DELETE FROM temperature_logs WHERE timestamp < :cutoff")
    void deleteOldLogs(long cutoff);

    @Query("SELECT * FROM temperature_logs ORDER BY timestamp DESC")
    List<TemperatureLog> getAllLogsDirect();

    @Query("SELECT * FROM temperature_logs WHERE timestamp >= :from AND timestamp <= :to ORDER BY timestamp DESC")
    List<TemperatureLog> getLogsBetween(long from, long to);

    @Query("SELECT * FROM temperature_logs WHERE timestamp >= :from ORDER BY timestamp DESC")
    List<TemperatureLog> getLogsAfter(long from);

    @Query("SELECT * FROM temperature_logs WHERE timestamp <= :to ORDER BY timestamp DESC")
    List<TemperatureLog> getLogsBefore(long to);
    @Query("SELECT DISTINCT(timestamp / 86400000) as day FROM temperature_logs")
    List<Long> getDistinctDays();
}
