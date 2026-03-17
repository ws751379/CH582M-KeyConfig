package com.example.keyconfig;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    
    private UsbHidManager usbManager;
    private Spinner[] spinners = new Spinner[6];
    private KeyValue[] keyConfigs = new KeyValue[6];
    private TextView tvStatus;
    private TextView tvLog;
    private Button btnConnect;
    private Button btnRead;
    private Button btnSave;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // 初始化 UI 控件
        tvStatus = findViewById(R.id.tvStatus);
        tvLog = findViewById(R.id.tvLog);
        btnConnect = findViewById(R.id.btnConnect);
        btnRead = findViewById(R.id.btnRead);
        btnSave = findViewById(R.id.btnSave);
        
        LinearLayout keyContainer = findViewById(R.id.keyContainer);
        
        // 创建 6 个键值配置 Spinner
        for (int i = 0; i < 6; i++) {
            View itemView = getLayoutInflater().inflate(R.layout.item_key_config, keyContainer, false);
            
            TextView labelView = itemView.findViewById(R.id.tvKeyLabel);
            Spinner spinner = itemView.findViewById(R.id.spinnerKey);
            
            labelView.setText("按键 " + (i + 1));
            
            // 设置 Spinner 选项
            ArrayAdapter<KeyValue> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, KeyValue.KEYBOARD_KEYS);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);
            
            spinners[i] = spinner;
            keyContainer.addView(itemView);
        }
        
        // 初始化键值配置
        for (int i = 0; i < 6; i++) {
            keyConfigs[i] = KeyValue.KEYBOARD_KEYS[0]; // 默认"无动作"
        }
        
        // 初始化 USB 管理器
        usbManager = new UsbHidManager(this);
        usbManager.setUsbEventListener(new UsbHidManager.UsbEventListener() {
            @Override
            public void onConnected() {
                runOnUiThread(() -> {
                    updateStatus("设备已连接");
                    btnConnect.setText("断开");
                    btnRead.setEnabled(true);
                    btnSave.setEnabled(true);
                });
            }
            
            @Override
            public void onDisconnected() {
                runOnUiThread(() -> {
                    updateStatus("设备已断开");
                    btnConnect.setText("连接");
                    btnRead.setEnabled(false);
                    btnSave.setEnabled(false);
                });
            }
            
            @Override
            public void onDataReceived(byte[] data) {
                runOnUiThread(() -> {
                    appendLog("收到数据: " + bytesToHex(data));
                    // 解析数据并更新界面
                    if (data.length >= 6) {
                        for (int i = 0; i < 6 && i < data.length; i++) {
                            // 根据 data[i] 查找对应的 KeyValue
                            // 这里需要根据实际协议解析
                        }
                    }
                });
            }
            
            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    appendLog("错误: " + message);
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                });
            }
        });
        
        // 连接/断开按钮
        btnConnect.setOnClickListener(v -> {
            if (usbManager.getConnection() != null) {
                usbManager.closeConnection();
            } else {
                appendLog("正在连接...");
                usbManager.requestConnection();
            }
        });
        
        // 读取配置按钮
        btnRead.setOnClickListener(v -> {
            appendLog("读取配置...");
            usbManager.readConfig();
        });
        
        // 保存配置按钮
        btnSave.setOnClickListener(v -> {
            // 从 Spinner 获取选中的键值
            for (int i = 0; i < 6; i++) {
                KeyValue selected = (KeyValue) spinners[i].getSelectedItem();
                if (selected != null) {
                    keyConfigs[i] = selected;
                    usbManager.writeKeyConfig(i, keyConfigs[i]);
                }
            }
            appendLog("写入配置...");
        });
        
        // 初始状态
        btnRead.setEnabled(false);
        btnSave.setEnabled(false);
        
        updateStatus("设备未连接");
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // 每次回到前台检查 USB 设备
        usbManager.checkConnectedDevices();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        usbManager.release();
    }
    
    private void updateStatus(String text) {
        if (tvStatus != null) {
            tvStatus.setText(text);
        }
    }
    
    private void appendLog(String text) {
        if (tvLog != null) {
            String currentLog = tvLog.getText().toString();
            if (currentLog.length() > 2000) {
                currentLog = currentLog.substring(1000); // 限制日志长度
            }
            tvLog.setText(currentLog + "\n" + text);
        }
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString();
    }
}
