package com.example.keyconfig;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    
    private UsbHidManager usbManager;
    private TextView tvStatus, tvLog;
    private View statusDot;
    private Button btnConnect, btnRead, btnSave, btnReset;
    private LinearLayout keyContainer;
    
    private KeyConfig[] keyConfigs = new KeyConfig[6];
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    
    private static class KeyConfig {
        int type = KeyValue.TYPE_NONE;
        int code = 0;
        int codeH = 0;
        int mod = 0;
        int selectedIndex = 0;
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initViews();
        initUsbManager();
        buildKeyUI();
    }
    
    private void initViews() {
        tvStatus = findViewById(R.id.tvStatus);
        tvLog = findViewById(R.id.tvLog);
        statusDot = findViewById(R.id.statusDot);
        btnConnect = findViewById(R.id.btnConnect);
        btnRead = findViewById(R.id.btnRead);
        btnSave = findViewById(R.id.btnSave);
        btnReset = findViewById(R.id.btnReset);
        keyContainer = findViewById(R.id.keyContainer);
        
        btnConnect.setOnClickListener(v -> connectDevice());
        btnRead.setOnClickListener(v -> readConfig());
        btnSave.setOnClickListener(v -> saveConfig());
        btnReset.setOnClickListener(v -> resetDefaults());
    }
    
    private void initUsbManager() {
        usbManager = new UsbHidManager(this);
        usbManager.setOnConnectionListener(new UsbHidManager.OnConnectionListener() {
            @Override
            public void onConnected() {
                runOnUiThread(() -> {
                    updateConnectionState(true);
                    log("设备已连接");
                    readConfig();
                });
            }
            
            @Override
            public void onDisconnected() {
                runOnUiThread(() -> {
                    updateConnectionState(false);
                    log("设备已断开");
                });
            }
            
            @Override
            public void onDataReceived(byte[] data) {
                runOnUiThread(() -> handleReceivedData(data));
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    log("错误: " + error);
                    Toast.makeText(MainActivity.this, error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private void buildKeyUI() {
        keyContainer.removeAllViews();
        
        for (int i = 0; i < 6; i++) {
            keyConfigs[i] = new KeyConfig();
            
            View itemView = LayoutInflater.from(this).inflate(R.layout.item_key, keyContainer, false);
            
            TextView tvNumber = itemView.findViewById(R.id.tvKeyNumber);
            TextView tvTitle = itemView.findViewById(R.id.tvKeyTitle);
            Spinner spinner = itemView.findViewById(R.id.spinnerKey);
            
            tvNumber.setText("K" + (i + 1));
            tvTitle.setText("按键 " + (i + 1));
            
            // 设置 Spinner
            ArrayAdapter<KeyValue> adapter = new ArrayAdapter<>(this, 
                android.R.layout.simple_spinner_item, KeyValue.KEYBOARD_KEYS);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);
            
            final int keyIndex = i;
            spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    KeyValue kv = KeyValue.KEYBOARD_KEYS[position];
                    keyConfigs[keyIndex].type = kv.type;
                    keyConfigs[keyIndex].code = kv.code;
                    keyConfigs[keyIndex].codeH = kv.codeH;
                    keyConfigs[keyIndex].mod = kv.mod;
                    keyConfigs[keyIndex].selectedIndex = position;
                    updateModifierButtons(itemView, keyIndex);
                }
                
                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
            
            // 修饰键按钮
            setupModifierButton(itemView, R.id.btnCtrl, 0x01, i);
            setupModifierButton(itemView, R.id.btnShift, 0x02, i);
            setupModifierButton(itemView, R.id.btnAlt, 0x04, i);
            setupModifierButton(itemView, R.id.btnWin, 0x08, i);
            
            keyContainer.addView(itemView);
        }
    }
    
    private void setupModifierButton(View parent, int btnId, int bit, int keyIndex) {
        ToggleButton btn = parent.findViewById(btnId);
        btn.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                keyConfigs[keyIndex].mod |= bit;
            } else {
                keyConfigs[keyIndex].mod &= ~bit;
            }
        });
    }
    
    private void updateModifierButtons(View itemView, int keyIndex) {
        boolean isKeyboard = keyConfigs[keyIndex].type == KeyValue.TYPE_KEYBOARD;
        int mod = keyConfigs[keyIndex].mod;
        
        ToggleButton btnCtrl = itemView.findViewById(R.id.btnCtrl);
        ToggleButton btnShift = itemView.findViewById(R.id.btnShift);
        ToggleButton btnAlt = itemView.findViewById(R.id.btnAlt);
        ToggleButton btnWin = itemView.findViewById(R.id.btnWin);
        
        btnCtrl.setEnabled(isKeyboard);
        btnShift.setEnabled(isKeyboard);
        btnAlt.setEnabled(isKeyboard);
        btnWin.setEnabled(isKeyboard);
        
        if (isKeyboard) {
            btnCtrl.setChecked((mod & 0x01) != 0);
            btnShift.setChecked((mod & 0x02) != 0);
            btnAlt.setChecked((mod & 0x04) != 0);
            btnWin.setChecked((mod & 0x08) != 0);
        }
    }
    
    private void connectDevice() {
        if (usbManager.isConnected()) {
            usbManager.closeDevice();
        } else {
            log("正在连接设备...");
            usbManager.requestConnection();
        }
    }
    
    private void updateConnectionState(boolean connected) {
        if (connected) {
            tvStatus.setText("设备已连接");
            statusDot.setBackgroundResource(R.drawable.dot_connected);
            btnConnect.setText("断开连接");
            btnConnect.setEnabled(true);
            btnRead.setEnabled(true);
            btnSave.setEnabled(true);
            btnReset.setEnabled(true);
        } else {
            tvStatus.setText("设备未连接");
            statusDot.setBackgroundResource(R.drawable.dot_disconnected);
            btnConnect.setText("连接 USB 设备");
            btnConnect.setEnabled(true);
            btnRead.setEnabled(false);
            btnSave.setEnabled(false);
            btnReset.setEnabled(false);
        }
    }
    
    private void readConfig() {
        log("读取配置...");
        usbManager.readConfig();
    }
    
    private void handleReceivedData(byte[] data) {
        if (data.length < 1 || data[0] != 0x52) return;
        
        // 解析6键配置
        for (int i = 0; i < 6; i++) {
            int base = 1 + i * 8;
            if (base + 3 >= data.length) break;
            
            keyConfigs[i].type = data[base] & 0xFF;
            keyConfigs[i].code = data[base + 1] & 0xFF;
            keyConfigs[i].codeH = data[base + 2] & 0xFF;
            keyConfigs[i].mod = data[base + 3] & 0xFF;
            
            // 更新 UI
            updateKeyUI(i);
        }
        
        log("配置读取成功");
    }
    
    private void updateKeyUI(int keyIndex) {
        View itemView = keyContainer.getChildAt(keyIndex);
        if (itemView == null) return;
        
        Spinner spinner = itemView.findViewById(R.id.spinnerKey);
        
        // 查找匹配的键值
        int bestIndex = 0;
        for (int j = 0; j < KeyValue.KEYBOARD_KEYS.length; j++) {
            KeyValue kv = KeyValue.KEYBOARD_KEYS[j];
            if (kv.type == keyConfigs[keyIndex].type && 
                kv.code == keyConfigs[keyIndex].code &&
                kv.codeH == keyConfigs[keyIndex].codeH) {
                bestIndex = j;
                if (kv.mod == keyConfigs[keyIndex].mod) {
                    bestIndex = j;
                    break;
                }
            }
        }
        
        keyConfigs[keyIndex].selectedIndex = bestIndex;
        spinner.setSelection(bestIndex);
        updateModifierButtons(itemView, keyIndex);
    }
    
    private void saveConfig() {
        log("保存配置...");
        new Thread(() -> {
            for (int i = 0; i < 6; i++) {
                usbManager.writeKeyConfig(i, keyConfigs[i].type, keyConfigs[i].code, 
                    keyConfigs[i].codeH, keyConfigs[i].mod);
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            runOnUiThread(() -> {
                log("配置已保存");
                Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }
    
    private void resetDefaults() {
        new AlertDialog.Builder(this)
            .setTitle("恢复默认")
            .setMessage("确认恢复默认配置（A B C D E F）？")
            .setPositiveButton("确定", (dialog, which) -> {
                log("恢复默认配置...");
                
                int[] defaultCodes = {0x04, 0x05, 0x06, 0x07, 0x08, 0x09};
                
                new Thread(() -> {
                    for (int i = 0; i < 6; i++) {
                        keyConfigs[i].type = KeyValue.TYPE_KEYBOARD;
                        keyConfigs[i].code = defaultCodes[i];
                        keyConfigs[i].codeH = 0;
                        keyConfigs[i].mod = 0;
                        
                        usbManager.writeKeyConfig(i, keyConfigs[i].type, keyConfigs[i].code,
                            keyConfigs[i].codeH, keyConfigs[i].mod);
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    
                    runOnUiThread(() -> {
                        for (int i = 0; i < 6; i++) {
                            updateKeyUI(i);
                        }
                        log("已恢复默认");
                        Toast.makeText(this, "恢复成功", Toast.LENGTH_SHORT).show();
                    });
                }).start();
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    private void log(String message) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String logText = "[" + time + "] " + message + "\n" + tvLog.getText().toString();
        tvLog.setText(logText);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (usbManager != null) {
            usbManager.unregister();
        }
    }
}
