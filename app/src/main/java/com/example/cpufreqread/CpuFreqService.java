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
    private int freshTime = 1000;

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

        if (intent != null){
            freshTime = intent.getIntExtra("fresh_time",1000); // 获取数据
        }
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

        File csvFilePath  = getFilesDir();
        File csvFile  = new File(csvFilePath, "cpu_frequency.csv");

        // 获取所有policy目录
        String[] policies = cpufreqDir.list((dir, name) -> name.startsWith("policy"));
        if (policies != null) {
            StringBuilder csvBuilder = new StringBuilder();

            // 检查CSV文件是否存在并且是否包含列名
            boolean isFirstWrite = !csvFile.exists() || csvFile.length() == 0;
            // 添加列名
            if (isFirstWrite) {
                for (String policy : policies) {
                    csvBuilder.append(policy).append(",");
                }
                // 移除最后一个逗号并换行
                csvBuilder.setLength(csvBuilder.length() - 1);
                csvBuilder.append("\n");
            }

            // 添加值
            StringBuilder valuesBuilder = new StringBuilder();
            for (String policy : policies) {
                String scalingCurFreqPath = cpufreqDirPath + policy + "/scaling_cur_freq";
                String scalingCurFreqValue = readScalingCurFreq(scalingCurFreqPath);
                Log.i(TAG,"policy "+policy+", scaling_cur_freq: "+scalingCurFreqValue);
                System.out.println(policy + ": " + scalingCurFreqValue);

                // 添加当前频率值
                valuesBuilder.append(scalingCurFreqValue).append(",");
            }
            // 移除最后一个逗号
            valuesBuilder.setLength(valuesBuilder.length() - 1);
            valuesBuilder.append("\n");

            // 将数据写入CSV文件
            if (isFirstWrite) {
                writeToFile(csvBuilder.toString(), true); // 写入列名
            }
            writeToFile(valuesBuilder.toString(), false); // 追加值
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

    private void writeToFile(String data, boolean isHeader) {
        try {
            File path = getFilesDir();
            File file = new File(path, "cpu_frequency.csv");

            if (!path.exists()) {
                path.mkdirs(); // 创建目录
            }
            FileWriter writer = new FileWriter(file, !isHeader); // 根据标志选择模式
            writer.write(data);
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
