package com.example.keyconfig;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbManager;
import android.util.Log;

import java.util.HashMap;

public class UsbHidManager {
    private static final String TAG = "UsbHidManager";
    private static final String ACTION_USB_PERMISSION = "com.example.keyconfig.USB_PERMISSION";
    
    // CH582M VID/PID
    private static final int VENDOR_ID = 0x1A86;
    private static final int PRODUCT_ID = 0x0061;
    
    // HID Report IDs
    private static final byte CMD_READ = 0x52;  // 'R'
    private static final byte CMD_WRITE = 0x57; // 'W'
    
    private Context context;
    private UsbManager usbManager;
    private UsbDevice device;
    private UsbDeviceConnection connection;
    private UsbInterface hidInterface;
    private UsbEndpoint endpointIn;
    private UsbEndpoint endpointOut;
    
    private OnConnectionListener listener;
    
    public interface OnConnectionListener {
        void onConnected();
        void onDisconnected();
        void onDataReceived(byte[] data);
        void onError(String error);
    }
    
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            openDevice(device);
                        }
                    } else {
                        if (listener != null) listener.onError("USB 权限被拒绝");
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null && device.getVendorId() == VENDOR_ID && device.getProductId() == PRODUCT_ID) {
                    closeDevice();
                }
            }
        }
    };
    
    public UsbHidManager(Context context) {
        this.context = context;
        this.usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        context.registerReceiver(usbReceiver, filter);
    }
    
    public void setOnConnectionListener(OnConnectionListener listener) {
        this.listener = listener;
    }
    
    public boolean requestConnection() {
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        
        for (UsbDevice dev : deviceList.values()) {
            if (dev.getVendorId() == VENDOR_ID && dev.getProductId() == PRODUCT_ID) {
                this.device = dev;
                
                if (usbManager.hasPermission(dev)) {
                    return openDevice(dev);
                } else {
                    PendingIntent permissionIntent = PendingIntent.getBroadcast(
                        context, 0, new Intent(ACTION_USB_PERMISSION), 
                        PendingIntent.FLAG_IMMUTABLE);
                    usbManager.requestPermission(dev, permissionIntent);
                    return true;
                }
            }
        }
        
        if (listener != null) listener.onError("未找到 CH582M 设备");
        return false;
    }
    
    private boolean openDevice(UsbDevice device) {
        connection = usbManager.openDevice(device);
        if (connection == null) {
            if (listener != null) listener.onError("无法打开设备");
            return false;
        }
        
        // 查找 HID 接口 (Usage Page 0xFF00)
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface intf = device.getInterface(i);
            
            UsbEndpoint in = null;
            UsbEndpoint out = null;
            
            for (int j = 0; j < intf.getEndpointCount(); j++) {
                UsbEndpoint ep = intf.getEndpoint(j);
                if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_INT) {
                    if (ep.getDirection() == UsbConstants.USB_DIR_IN) {
                        in = ep;
                    } else {
                        out = ep;
                    }
                }
            }
            
            if (in != null && out != null) {
                hidInterface = intf;
                endpointIn = in;
                endpointOut = out;
                break;
            }
        }
        
        if (hidInterface == null) {
            if (listener != null) listener.onError("未找到 HID 接口");
            return false;
        }
        
        if (!connection.claimInterface(hidInterface, true)) {
            if (listener != null) listener.onError("无法占用接口");
            return false;
        }
        
        // 启动读取线程
        startReadThread();
        
        if (listener != null) listener.onConnected();
        return true;
    }
    
    private void startReadThread() {
        new Thread(() -> {
            byte[] buffer = new byte[64];
            while (connection != null) {
                int ret = connection.bulkTransfer(endpointIn, buffer, buffer.length, 1000);
                if (ret > 0 && listener != null) {
                    byte[] data = new byte[ret];
                    System.arraycopy(buffer, 0, data, 0, ret);
                    listener.onDataReceived(data);
                }
            }
        }).start();
    }
    
    public void readConfig() {
        if (connection == null || endpointOut == null) return;
        
        byte[] buffer = new byte[64];
        buffer[0] = CMD_READ;
        connection.bulkTransfer(endpointOut, buffer, buffer.length, 1000);
    }
    
    public void writeKeyConfig(int index, int type, int code, int codeH, int mod) {
        if (connection == null || endpointOut == null) return;
        
        byte[] buffer = new byte[64];
        buffer[0] = CMD_WRITE;
        buffer[1] = (byte) index;
        buffer[2] = (byte) type;
        buffer[3] = (byte) code;
        buffer[4] = (byte) codeH;
        buffer[5] = (byte) mod;
        
        connection.bulkTransfer(endpointOut, buffer, buffer.length, 1000);
    }
    
    public void closeDevice() {
        if (connection != null) {
            if (hidInterface != null) {
                connection.releaseInterface(hidInterface);
            }
            connection.close();
            connection = null;
        }
        device = null;
        hidInterface = null;
        endpointIn = null;
        endpointOut = null;
        
        if (listener != null) listener.onDisconnected();
    }
    
    public boolean isConnected() {
        return connection != null;
    }
    
    public void unregister() {
        closeDevice();
        context.unregisterReceiver(usbReceiver);
    }
}
