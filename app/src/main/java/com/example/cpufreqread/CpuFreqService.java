package com.example.cpufreqread;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class CpuFreqService extends Service {
    private static final String TAG = "CpuFreqService";
    private static final String CHANNEL_ID = "CpuFreqServiceChannel";
    private Handler handler;
    private Runnable runnable;
    private int freshTime;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG,"onCreate called!");

        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        freshTime = intent.getIntExtra("fresh_time",1000); // 获取数据

        startForeground(1, createNotification()); // 启动前台服务
        startLogging(freshTime); // 开始记录 CPU 频率
        return START_STICKY;
    }

    private void startLogging(int freshTime) {
        handler = new Handler(Looper.getMainLooper());
        runnable = new Runnable() {
            @Override
            public void run() {
                readCpuFreq();
                handler.postDelayed(this, freshTime); // 每秒执行一次
            }
        };
        handler.post(runnable);
    }
    private Notification createNotification() {
        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("CPU Frequency Logger")
                .setContentText("Logging CPU frequency...")
                .setSmallIcon(R.drawable.ic_launcher_foreground) // 替换为您的图标
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "CPU Frequency Service",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private void readCpuFreq() {
        String cpufreqDirPath = "/sys/devices/system/cpu/cpufreq/";
        File cpufreqDir = new File(cpufreqDirPath);

        // 获取所有policy目录
        String[] policies = cpufreqDir.list((dir, name) -> name.startsWith("policy"));
        if (policies != null) {
            for (String policy : policies) {
                String scalingCurFreqPath = cpufreqDirPath + policy + "/scaling_cur_freq";
                String scalingCurFreqValue = readScalingCurFreq(scalingCurFreqPath);
                Log.i(TAG,"policy "+policy+", scaling_cur_freq: "+scalingCurFreqValue);
                System.out.println(policy + ": " + scalingCurFreqValue);

                // 将数据写入文件
                writeToFile(policy+":"+scalingCurFreqValue);

            }
        } else {
            System.out.println("No policy directories found.");
        }
    }

    private String readScalingCurFreq(String filePath) {
        StringBuilder freqValue = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            if ((line = reader.readLine()) != null) {
                freqValue.append(line.trim());
            }
        } catch (IOException e) {
            e.printStackTrace();
            return "Error reading file";
        }

        return freqValue.toString();
    }

    private void writeToFile(String data) {
        try {
//            File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            File path = getFilesDir();
            File file = new File(path, "cpu_frequency.txt");

            if (!path.exists()) {
                path.mkdirs(); // 创建目录
            }
            FileWriter writer = new FileWriter(file, true); // 以追加模式写入
            writer.append(data).append("\n"); // 每次写入后添加一个换行符
            writer.close();
            Log.d(TAG, "Data written to file: " + data);

        } catch (IOException e) {
            Log.e(TAG, "Error writing data to file", e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(runnable); // 停止定时任务
    }
}
