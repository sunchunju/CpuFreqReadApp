package com.example.cpufreqread;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.View;

import androidx.core.app.ActivityCompat;

import com.example.cpufreqread.databinding.ActivityMainBinding;
import com.lzf.easyfloat.EasyFloat;
import com.lzf.easyfloat.enums.ShowPattern;
import com.lzf.easyfloat.enums.SidePattern;
import com.lzf.easyfloat.interfaces.OnInvokeView;

import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private static final String TAG = "CpuFreqLogger";
    private TextView cpuFreqTextView;
    private Button startStopButton;
    private EditText freshTimeEditView;
    private TextView cpuFreqFloatTextView;
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
        freshTimeEditView = findViewById(R.id.refTimeValueEt);
        startStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isLogging) {
                    stopLogging();
                    dismissPopupWindow();
                } else {
                    startLogging();
                    showPopupWindow();
                    readCpuFreqAndUpdateUI();
                }
            }
        });

        //删除已经存在的cpu_frequency.txt文件
        deleteTempFile();
        checkPermission();
    }

    private void dismissPopupWindow() {
        EasyFloat.dismiss();
    }

    private void showPopupWindow() {
        EasyFloat.with(getApplicationContext())
                .setShowPattern(ShowPattern.ALL_TIME)
                .setSidePattern(SidePattern.RESULT_SIDE)
                .setImmersionStatusBar(true)
                .setGravity(Gravity.END, -20,10)
                .setDragEnable(true)
                .setLayout(R.layout.float_app, new OnInvokeView() {
                    @Override
                    public void invoke(View view) {
                        cpuFreqFloatTextView = view.findViewById(R.id.tvCpuFreqValue);
                    }
                })
                .show();


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
        //set fresh time
        String freshTimeValue = freshTimeEditView.getText().toString();
        serviceIntent.putExtra("fresh_time",Integer.parseInt(freshTimeValue.isEmpty()?"1000":freshTimeValue));
        startForegroundService(serviceIntent); // 启动前台服务
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
                ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.POST_NOTIFICATIONS) ||
                ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.SYSTEM_ALERT_WINDOW) ;
        Log.i("MainActivity","checkPermission need = "+need);
        if(need) {
            ActivityCompat.requestPermissions(this, new
                    String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE, android.Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.SYSTEM_ALERT_WINDOW}, 0);
        }
    }

    private void readCpuFreq() {
        String cpufreqDirPath = "/sys/devices/system/cpu/cpufreq/";
        File cpufreqDir = new File(cpufreqDirPath);
        cpuFreqTextView.setText("");
        cpuFreqFloatTextView.setText("");

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
                cpuFreqFloatTextView.append(policy+": "+scalingCurFreqValue+"\n");
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