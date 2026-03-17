package com.example.keyconfig;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    
    private UsbHidManager usbManager;
    private Spinner[] spinners = new Spinner[6];
    private KeyValue[] keyConfigs = new KeyValue[6];
    private TextView statusText;
    private Button connectButton;
    private Button readButton;
    private Button writeButton;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        statusText = findViewById(R.id.status_text);
        connectButton = findViewById(R.id.btn_connect);
        readButton = findViewById(R.id.btn_read);
        writeButton = findViewById(R.id.btn_write);
        
        spinners[0] = findViewById(R.id.spinner_key1);
        spinners[1] = findViewById(R.id.spinner_key2);
        spinners[2] = findViewById(R.id.spinner_key3);
        spinners[3] = findViewById(R.id.spinner_key4);
        spinners[4] = findViewById(R.id.spinner_key5);
        spinners[5] = findViewById(R.id.spinner_key6);
        
        // 初始化键值配置
        for (int i = 0; i < 6; i++) {
            keyConfigs[i] = KeyValue.KEYBOARD_KEYS[0]; // 默认"无动作"
        }
        
        // 设置 Spinner 选项
        ArrayAdapter<KeyValue> adapter = new ArrayAdapter<>(
            this, android.R.layout.simple_spinner_item, KeyValue.KEYBOARD_KEYS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        
        for (Spinner spinner : spinners) {
            spinner.setAdapter(adapter);
        }
        
        // 初始化 USB 管理器
        usbManager = new UsbHidManager(this);
        usbManager.setUsbEventListener(new UsbHidManager.UsbEventListener() {
            @Override
            public void onConnected() {
                runOnUiThread(() -> {
                    updateStatus("设备已连接");
                    connectButton.setText("断开");
                    readButton.setEnabled(true);
                    writeButton.setEnabled(true);
                });
            }
            
            @Override
            public void onDisconnected() {
                runOnUiThread(() -> {
                    updateStatus("设备已断开");
                    connectButton.setText("连接");
                    readButton.setEnabled(false);
                    writeButton.setEnabled(false);
                });
            }
            
            @Override
            public void onDataReceived(byte[] data) {
                runOnUiThread(() -> {
                    updateStatus("收到数据: " + bytesToHex(data));
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
                    updateStatus("错误: " + message);
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                });
            }
        });
        
        // 连接/断开按钮
        connectButton.setOnClickListener(v -> {
            if (usbManager.getConnection() != null) {
                usbManager.closeConnection();
            } else {
                usbManager.requestConnection();
            }
        });
        
        // 读取配置按钮
        readButton.setOnClickListener(v -> {
            usbManager.readConfig();
            updateStatus("读取配置...");
        });
        
        // 写入配置按钮
        writeButton.setOnClickListener(v -> {
            // 从 Spinner 获取选中的键值
            for (int i = 0; i < 6; i++) {
                KeyValue selected = (KeyValue) spinners[i].getSelectedItem();
                if (selected != null) {
                    keyConfigs[i] = selected;
                    usbManager.writeKeyConfig(i, keyConfigs[i]);
                }
            }
            updateStatus("写入配置...");
        });
        
        // 初始状态
        readButton.setEnabled(false);
        writeButton.setEnabled(false);
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
        if (statusText != null) {
            statusText.setText(text);
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
