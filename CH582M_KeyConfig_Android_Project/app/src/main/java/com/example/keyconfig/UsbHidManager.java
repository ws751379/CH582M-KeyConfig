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

    // USB 常量
    private static final int USB_DIR_IN = 128;           // 0x80
    private static final int USB_ENDPOINT_XFER_INT = 3;  // 中断传输

    private Context context;
    private UsbManager usbManager;
    private UsbDevice device;
    private UsbDeviceConnection connection;

    // 配置通道端点（接口1，EP2）
    private UsbInterface configInterface;
    private UsbEndpoint ep2In;   // 0x82 - 读配置应答
    private UsbEndpoint ep2Out;  // 0x02 - 写配置/查询

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
            if (listener != null) {
                listener.onError("无法打开 USB 连接");
            }
            return false;
        }

        Log.d(TAG, "USB 连接成功");

        // 查找配置接口（接口1，HID Class=0x03）
        configInterface = findConfigInterface(device);
        if (configInterface == null) {
            Log.e(TAG, "未找到配置接口（接口1）");
            connection.close();
            connection = null;
            if (listener != null) {
                listener.onError("未找到配置接口");
            }
            return false;
        }

        Log.d(TAG, "找到配置接口，接口号: " + configInterface.getId());

        if (!connection.claimInterface(configInterface, true)) {
            Log.e(TAG, "claimInterface 失败");
            connection.close();
            connection = null;
            if (listener != null) {
                listener.onError("无法占用 USB 接口");
            }
            return false;
        }

        Log.d(TAG, "配置接口占用成功");

        // 查找 EP2 端点
        findEp2Endpoints(configInterface);

        if (ep2Out == null) {
            Log.e(TAG, "未找到 EP2 OUT 端点");
            connection.close();
            connection = null;
            if (listener != null) {
                listener.onError("未找到输出端点");
            }
            return false;
        }

        Log.d(TAG, "EP2 IN: " + (ep2In != null ? "0x" + Integer.toHexString(ep2In.getAddress()) : "无"));
        Log.d(TAG, "EP2 OUT: 0x" + Integer.toHexString(ep2Out.getAddress()));

        // 启动读取线程
        if (ep2In != null) {
            startReadThread();
        }

        Log.d(TAG, "设备连接成功！");
        if (listener != null) {
            listener.onConnected();
        }

        return true;
    }

    /**
     * 查找配置接口（接口1，HID Class=0x03）
     * CH582M 有两个接口：
     *   接口0：标准键盘
     *   接口1：自定义配置（我们要用的）
     */
    private UsbInterface findConfigInterface(UsbDevice device) {
        Log.d(TAG, "查找配置接口，共 " + device.getInterfaceCount() + " 个接口");

        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface intf = device.getInterface(i);
            Log.d(TAG, "接口 " + i + ": 类=" + intf.getInterfaceClass() +
                      ", 子类=" + intf.getInterfaceSubclass() +
                      ", 协议=" + intf.getInterfaceProtocol() +
                      ", 端点数=" + intf.getEndpointCount());

            // HID 接口: Class=0x03
            // 配置接口是第二个 HID 接口（接口1），有2个端点
            if (intf.getInterfaceClass() == 0x03 && intf.getEndpointCount() == 2) {
                Log.d(TAG, "找到配置接口（接口" + i + "）");
                return intf;
            }
        }
        return null;
    }

    /**
     * 查找 EP2 端点
     * EP2 IN (0x82): 设备→主机，读配置应答
     * EP2 OUT (0x02): 主机→设备，写配置/查询
     */
    private void findEp2Endpoints(UsbInterface intf) {
        ep2In = null;
        ep2Out = null;

        for (int i = 0; i < intf.getEndpointCount(); i++) {
            UsbEndpoint ep = intf.getEndpoint(i);
            int addr = ep.getAddress();
            int dir = ep.getDirection();
            int type = ep.getType();

            Log.d(TAG, "端点 " + i + ": 地址=0x" + Integer.toHexString(addr) +
                      ", 类型=" + type + ", 方向=" + dir);

            // EP2 IN: 地址 0x82 (130), 方向 IN (128)
            // EP2 OUT: 地址 0x02 (2), 方向 OUT (0)
            if (addr == 0x82) {
                ep2In = ep;
                Log.d(TAG, "  -> EP2 IN (读配置)");
            } else if (addr == 0x02) {
                ep2Out = ep;
                Log.d(TAG, "  -> EP2 OUT (写配置)");
            }
        }
    }

    private void startReadThread() {
        isRunning = true;
        readThread = new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] buffer = new byte[64];
                while (isRunning && connection != null && ep2In != null) {
                    int bytesReceived = connection.bulkTransfer(ep2In, buffer, buffer.length, 1000);
                    if (bytesReceived > 0) {
                        Log.d(TAG, "收到 " + bytesReceived + " 字节数据");
                        byte[] data = new byte[bytesReceived];
                        System.arraycopy(buffer, 0, data, 0, bytesReceived);
                        if (listener != null) {
                            listener.onDataReceived(data);
                        }
                    } else if (bytesReceived < 0) {
                        // 超时或错误，继续循环
                        if (bytesReceived != -1) {
                            Log.e(TAG, "读取错误: " + bytesReceived);
                        }
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
            if (configInterface != null) {
                connection.releaseInterface(configInterface);
                configInterface = null;
            }
            connection.close();
            connection = null;
        }

        ep2In = null;
        ep2Out = null;

        if (listener != null) {
            listener.onDisconnected();
        }
    }

    /**
     * 发送命令到 EP2 OUT
     * 必须发送 64 字节（CH582M 协议要求）
     */
    public void sendCommand(byte[] command) {
        if (connection == null || ep2Out == null) {
            Log.e(TAG, "未连接或没有 EP2 OUT 端点");
            if (listener != null) {
                listener.onError("设备未连接");
            }
            return;
        }

        // CH582M 要求 64 字节包
        byte[] packet = new byte[64];
        System.arraycopy(command, 0, packet, 0, Math.min(command.length, 64));

        Log.d(TAG, "发送命令: " + bytesToHex(packet));
        int result = connection.bulkTransfer(ep2Out, packet, packet.length, 1000);
        Log.d(TAG, "发送结果: " + result + " 字节");

        if (result != packet.length) {
            Log.e(TAG, "发送失败，返回值: " + result);
            if (listener != null) {
                listener.onError("发送命令失败");
            }
        } else {
            Log.d(TAG, "发送成功");
        }
    }

    /**
     * 读取配置
     * 协议: [0]='R'(0x52) [1..63]=0
     */
    public void readConfig() {
        byte[] command = new byte[64];
        command[0] = 0x52;  // 'R'
        // 其余自动为 0
        sendCommand(command);
    }

    /**
     * 写入键值配置
     * 协议: [0]='W'(0x57) [1]=键索引 [2]=类型 [3]=keycode_lo [4]=keycode_hi [5]=modifier
     */
    public void writeKeyConfig(int keyIndex, KeyValue keyValue) {
        byte[] command = new byte[64];
        command[0] = 0x57;           // 'W' - 写命令
        command[1] = (byte) keyIndex;
        command[2] = (byte) keyValue.type;
        command[3] = (byte) keyValue.code;
        command[4] = (byte) keyValue.codeH;
        command[5] = (byte) keyValue.mod;
        // 其余自动为 0
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
        for (int i = 0; i < bytes.length && i < 16; i++) {  // 只显示前16字节
            sb.append(String.format("%02X ", bytes[i]));
        }
        if (bytes.length > 16) {
            sb.append("...");
        }
        return sb.toString();
    }
}
