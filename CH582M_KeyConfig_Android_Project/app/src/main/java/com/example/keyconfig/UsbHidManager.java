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
import android.hardware.usb.UsbManager;
import android.util.Log;

import java.util.HashMap;

public class UsbHidManager {
    private static final String TAG = "UsbHidManager";
    private static final String ACTION_USB_PERMISSION = "com.example.keyconfig.USB_PERMISSION";

    private static final int VID = 6790;  // 0x1A86 - WCH
    private static final int PID = 97;    // 0x0061

    // USB 常量（数值直接定义，避免 UsbEndpoint 常量访问问题）
    private static final int USB_DIR_IN = 128;           // 0x80
    private static final int USB_ENDPOINT_XFER_INT = 3;  // 中断传输

    private Context context;
    private UsbManager usbManager;
    private UsbDevice device;
    private UsbDeviceConnection connection;
    private UsbEndpoint endpointIn;
    private UsbEndpoint endpointOut;

    private Thread readThread;
    private volatile boolean isRunning = false;

    private UsbEventListener listener;

    public interface UsbEventListener {
        void onConnected();
        void onDisconnected();
        void onDataReceived(byte[] data);
        void onError(String message);
    }

    public UsbHidManager(Context context) {
        this.context = context;
        this.usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        registerPermissionReceiver();
    }

    public void setUsbEventListener(UsbEventListener listener) {
        this.listener = listener;
    }

    public UsbDeviceConnection getConnection() {
        return connection;
    }

    private void registerPermissionReceiver() {
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        context.registerReceiver(usbPermissionReceiver, filter);
    }

    private final BroadcastReceiver usbPermissionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                UsbDevice grantedDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                if (grantedDevice == null) {
                    Log.e(TAG, "权限回调中设备为 null");
                    return;
                }

                Log.d(TAG, "权限回调 - 设备: " + grantedDevice.getDeviceName() +
                          ", VID=" + grantedDevice.getVendorId() +
                          ", PID=" + grantedDevice.getProductId());

                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    Log.d(TAG, "权限已授予");
                    device = grantedDevice;
                    openDevice();
                } else {
                    Log.e(TAG, "权限被拒绝");
                    if (listener != null) {
                        listener.onError("USB 权限被拒绝");
                    }
                }
            }
        }
    };

    public void requestConnection() {
        if (usbManager == null) {
            Log.e(TAG, "UsbManager 为 null");
            return;
        }

        Log.d(TAG, "开始查找 USB 设备...");

        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        Log.d(TAG, "找到 " + deviceList.size() + " 个 USB 设备");

        for (UsbDevice dev : deviceList.values()) {
            Log.d(TAG, "设备: VID=" + dev.getVendorId() + ", PID=" + dev.getProductId());

            if (dev.getVendorId() == VID && dev.getProductId() == PID) {
                Log.d(TAG, "匹配到目标设备！");
                device = dev;

                if (usbManager.hasPermission(dev)) {
                    Log.d(TAG, "已有权限，直接连接");
                    openDevice();
                } else {
                    Log.d(TAG, "没有权限，请求权限...");
                    requestPermission(dev);
                }
                return;
            }
        }

        Log.d(TAG, "未找到目标设备 VID=" + VID + " PID=" + PID);
        if (listener != null) {
            listener.onError("未找到 CH582M 设备");
        }
    }

    private void requestPermission(UsbDevice dev) {
        try {
            PendingIntent permissionIntent = PendingIntent.getBroadcast(
                context, 0, new Intent(ACTION_USB_PERMISSION),
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

            Log.d(TAG, "请求 USB 权限...");
            usbManager.requestPermission(dev, permissionIntent);
        } catch (Exception e) {
            Log.e(TAG, "请求权限失败", e);
            if (listener != null) {
                listener.onError("请求权限失败: " + e.getMessage());
            }
        }
    }

    public boolean openDevice() {
        if (device == null) {
            Log.e(TAG, "设备为 null，无法连接");
            if (listener != null) {
                listener.onError("设备未初始化");
            }
            return false;
        }

        Log.d(TAG, "开始打开设备: VID=" + device.getVendorId() + ", PID=" + device.getProductId());

        // 先关闭现有连接
        closeConnection();

        connection = usbManager.openDevice(device);

        if (connection == null) {
            Log.e(TAG, "UsbDeviceConnection.openDevice() 返回 null");
            Log.e(TAG, "可能原因：1) 接口已被占用 2) 没有权限 3) 设备已断开");
            if (listener != null) {
                listener.onError("无法打开 USB 连接");
            }
            return false;
        }

        Log.d(TAG, "USB 连接成功，查找 HID 接口...");

        UsbInterface hidInterface = findHidInterface(device);
        if (hidInterface == null) {
            Log.e(TAG, "未找到 HID 接口");
            connection.close();
            connection = null;
            if (listener != null) {
                listener.onError("未找到 HID 接口");
            }
            return false;
        }

        Log.d(TAG, "找到 HID 接口，接口号: " + hidInterface.getId());

        if (!connection.claimInterface(hidInterface, true)) {
            Log.e(TAG, "claimInterface 失败，接口可能被占用");
            connection.close();
            connection = null;
            if (listener != null) {
                listener.onError("无法占用 USB 接口");
            }
            return false;
        }

        Log.d(TAG, "HID 接口占用成功");

        // 查找端点
        findEndpoints(hidInterface);

        if (endpointIn == null) {
            Log.e(TAG, "未找到输入端点");
            connection.close();
            connection = null;
            if (listener != null) {
                listener.onError("未找到输入端点");
            }
            return false;
        }

        Log.d(TAG, "输入端点地址: 0x" + Integer.toHexString(endpointIn.getAddress()));
        Log.d(TAG, "输出端点地址: " + (endpointOut != null ? "0x" + Integer.toHexString(endpointOut.getAddress()) : "无"));

        // 启动读取线程
        startReadThread();

        Log.d(TAG, "设备连接成功！");
        if (listener != null) {
            listener.onConnected();
        }

        return true;
    }

    private UsbInterface findHidInterface(UsbDevice device) {
        UsbInterface hidInterface = null;
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface intf = device.getInterface(i);
            Log.d(TAG, "接口 " + i + ": 类=" + intf.getInterfaceClass() +
                      ", 子类=" + intf.getInterfaceSubclass() +
                      ", 协议=" + intf.getInterfaceProtocol());

            // HID 接口: Class=0x03
            if (intf.getInterfaceClass() == 0x03) {
                Log.d(TAG, "找到 HID 接口");
                hidInterface = intf;
                break;
            }
        }
        return hidInterface;
    }

    private void findEndpoints(UsbInterface intf) {
        endpointIn = null;
        endpointOut = null;

        for (int i = 0; i < intf.getEndpointCount(); i++) {
            UsbEndpoint ep = intf.getEndpoint(i);
            int dir = ep.getDirection();
            int type = ep.getType();

            Log.d(TAG, "端点 " + i + ": 地址=0x" + Integer.toHexString(ep.getAddress()) +
                      ", 类型=" + type +
                      ", 方向=" + dir);

            // 中断传输端点: type=3 (0x03)
            // 输入方向: dir=128 (0x80)
            if (type == USB_ENDPOINT_XFER_INT) {
                if (dir == USB_DIR_IN) {
                    endpointIn = ep;
                } else {
                    endpointOut = ep;
                }
            }
        }
    }

    private void startReadThread() {
        isRunning = true;
        readThread = new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] buffer = new byte[64];
                while (isRunning && connection != null) {
                    int bytesReceived = connection.bulkTransfer(endpointIn, buffer, buffer.length, 1000);
                    if (bytesReceived > 0) {
                        Log.d(TAG, "收到 " + bytesReceived + " 字节数据");
                        byte[] data = new byte[bytesReceived];
                        System.arraycopy(buffer, 0, data, 0, bytesReceived);
                        if (listener != null) {
                            listener.onDataReceived(data);
                        }
                    } else if (bytesReceived < 0) {
                        Log.e(TAG, "读取失败: " + bytesReceived);
                        break;
                    }
                }
            }
        });
        readThread.start();
    }

    public void closeConnection() {
        Log.d(TAG, "关闭连接");
        isRunning = false;

        if (readThread != null) {
            try {
                readThread.join(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            readThread = null;
        }

        if (connection != null) {
            connection.close();
            connection = null;
        }

        if (listener != null) {
            listener.onDisconnected();
        }
    }

    public void sendCommand(byte[] command) {
        if (connection == null || endpointOut == null) {
            Log.e(TAG, "未连接或没有输出端点");
            if (listener != null) {
                listener.onError("设备未连接");
            }
            return;
        }

        Log.d(TAG, "发送命令: " + bytesToHex(command));
        int result = connection.bulkTransfer(endpointOut, command, command.length, 1000);
        if (result != command.length) {
            Log.e(TAG, "发送失败，返回值: " + result);
            if (listener != null) {
                listener.onError("发送命令失败");
            }
        } else {
            Log.d(TAG, "发送成功");
        }
    }

    // 读取配置
    public void readConfig() {
        byte[] command = {0x01, 0x00};  // 假设的读取命令
        sendCommand(command);
    }

    // 写入键值配置
    public void writeKeyConfig(int keyIndex, KeyValue keyValue) {
        byte[] command = new byte[8];
        command[0] = 0x02;  // 写入命令
        command[1] = (byte) keyIndex;
        command[2] = (byte) keyValue.type;
        command[3] = (byte) keyValue.code;
        command[4] = (byte) keyValue.codeH;
        command[5] = (byte) keyValue.mod;
        sendCommand(command);
    }

    public void checkConnectedDevices() {
        requestConnection();
    }

    public void release() {
        closeConnection();
        try {
            context.unregisterReceiver(usbPermissionReceiver);
        } catch (Exception e) {
            e.printStackTrace();
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
