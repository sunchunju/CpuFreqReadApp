package com.example.cpufreqread;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;

import androidx.core.app.ActivityCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.cpufreqread.databinding.ActivityMainBinding;

import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private static final String TAG = "CpuFreqLogger";
    private TextView cpuFreqTextView;
    private Button startStopButton;
    private boolean isLogging = false;
    private Handler handler;
    private Runnable runnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        cpuFreqTextView = findViewById(R.id.cpuFreqTextView);
        startStopButton = findViewById(R.id.startBtn);
        startStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isLogging) {
                    stopLogging();
                } else {
                    startLogging();
                }
            }
        });

        //删除已经存在的cpu_frequency.txt文件
        deleteTempFile();
        checkPermission();
    }
    private void deleteTempFile() {
        File path = getFilesDir();
        File file = new File(path, "cpu_frequency.txt");
        // 检查文件是否存在
        if (file.exists()) {
            // 尝试删除文件
            boolean deleted = file.delete();
            if (deleted) {
                System.out.println("File deleted successfully: " + file.getAbsolutePath());
            } else {
                System.out.println("Failed to delete the file.");
            }
        } else {
            System.out.println("File does not exist: " + file.getAbsolutePath());
        }
    }

    private void startLogging() {
        isLogging = true;
        startStopButton.setText("Stop");

        Intent serviceIntent = new Intent(this, CpuFreqService.class);
        startForegroundService(serviceIntent); // 启动前台服务

        readCpuFreqAndUpdateUI();
    }

    private void readCpuFreqAndUpdateUI() {
        handler = new Handler();
        runnable = new Runnable() {
            @Override
            public void run() {
                readCpuFreq();
                handler.postDelayed(this, 1000); // 每秒执行一次
            }
        };
        handler.post(runnable);
    }

    private void stopLogging() {
        isLogging = false;
        startStopButton.setText("Start");
        Intent serviceIntent = new Intent(this, CpuFreqService.class);
        stopService(serviceIntent); // 停止前台服务
        handler.removeCallbacks(runnable);
    }

    private void checkPermission() {
        boolean need = ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) ||
                ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) ||
                ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.POST_NOTIFICATIONS);
        Log.i("MainActivity","checkPermission need = "+need);
        if(need) {
            ActivityCompat.requestPermissions(this, new
                    String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE, android.Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.POST_NOTIFICATIONS}, 0);
        }
    }

    private void readCpuFreq() {
        String cpufreqDirPath = "/sys/devices/system/cpu/cpufreq/";
        File cpufreqDir = new File(cpufreqDirPath);
        cpuFreqTextView.setText("");

        // 获取所有policy目录
        String[] policies = cpufreqDir.list((dir, name) -> name.startsWith("policy"));
        if (policies != null) {
            for (String policy : policies) {
                String scalingCurFreqPath = cpufreqDirPath + policy + "/scaling_cur_freq";
                String scalingCurFreqValue = readScalingCurFreq(scalingCurFreqPath);
                Log.i(TAG,"policy "+policy+", scaling_cur_freq: "+scalingCurFreqValue);
                System.out.println(policy + ": " + scalingCurFreqValue);

                // Update UI
                cpuFreqTextView.append(policy+": "+scalingCurFreqValue+"\n");
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
}