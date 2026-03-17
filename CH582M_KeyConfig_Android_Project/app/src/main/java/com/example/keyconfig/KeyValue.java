package com.example.keyconfig;

public class KeyValue {
    public static final int TYPE_NONE = 0x00;
    public static final int TYPE_KEYBOARD = 0x01;
    public static final int TYPE_CONSUMER = 0x02;
    
    public String label;
    public int type;
    public int code;
    public int codeH;
    public int mod;
    
    public KeyValue(String label, int type, int code, int codeH, int mod) {
        this.label = label;
        this.type = type;
        this.code = code;
        this.codeH = codeH;
        this.mod = mod;
    }
    
    @Override
    public String toString() {
        return label;
    }
    
    // 预定义的键值表
    public static KeyValue[] KEYBOARD_KEYS = {
        new KeyValue("无动作", TYPE_NONE, 0x00, 0, 0),
        // 字母
        new KeyValue("A", TYPE_KEYBOARD, 0x04, 0, 0),
        new KeyValue("B", TYPE_KEYBOARD, 0x05, 0, 0),
        new KeyValue("C", TYPE_KEYBOARD, 0x06, 0, 0),
        new KeyValue("D", TYPE_KEYBOARD, 0x07, 0, 0),
        new KeyValue("E", TYPE_KEYBOARD, 0x08, 0, 0),
        new KeyValue("F", TYPE_KEYBOARD, 0x09, 0, 0),
        new KeyValue("G", TYPE_KEYBOARD, 0x0A, 0, 0),
        new KeyValue("H", TYPE_KEYBOARD, 0x0B, 0, 0),
        new KeyValue("I", TYPE_KEYBOARD, 0x0C, 0, 0),
        new KeyValue("J", TYPE_KEYBOARD, 0x0D, 0, 0),
        new KeyValue("K", TYPE_KEYBOARD, 0x0E, 0, 0),
        new KeyValue("L", TYPE_KEYBOARD, 0x0F, 0, 0),
        new KeyValue("M", TYPE_KEYBOARD, 0x10, 0, 0),
        new KeyValue("N", TYPE_KEYBOARD, 0x11, 0, 0),
        new KeyValue("O", TYPE_KEYBOARD, 0x12, 0, 0),
        new KeyValue("P", TYPE_KEYBOARD, 0x13, 0, 0),
        new KeyValue("Q", TYPE_KEYBOARD, 0x14, 0, 0),
        new KeyValue("R", TYPE_KEYBOARD, 0x15, 0, 0),
        new KeyValue("S", TYPE_KEYBOARD, 0x16, 0, 0),
        new KeyValue("T", TYPE_KEYBOARD, 0x17, 0, 0),
        new KeyValue("U", TYPE_KEYBOARD, 0x18, 0, 0),
        new KeyValue("V", TYPE_KEYBOARD, 0x19, 0, 0),
        new KeyValue("W", TYPE_KEYBOARD, 0x1A, 0, 0),
        new KeyValue("X", TYPE_KEYBOARD, 0x1B, 0, 0),
        new KeyValue("Y", TYPE_KEYBOARD, 0x1C, 0, 0),
        new KeyValue("Z", TYPE_KEYBOARD, 0x1D, 0, 0),
        // 数字
        new KeyValue("1", TYPE_KEYBOARD, 0x1E, 0, 0),
        new KeyValue("2", TYPE_KEYBOARD, 0x1F, 0, 0),
        new KeyValue("3", TYPE_KEYBOARD, 0x20, 0, 0),
        new KeyValue("4", TYPE_KEYBOARD, 0x21, 0, 0),
        new KeyValue("5", TYPE_KEYBOARD, 0x22, 0, 0),
        new KeyValue("6", TYPE_KEYBOARD, 0x23, 0, 0),
        new KeyValue("7", TYPE_KEYBOARD, 0x24, 0, 0),
        new KeyValue("8", TYPE_KEYBOARD, 0x25, 0, 0),
        new KeyValue("9", TYPE_KEYBOARD, 0x26, 0, 0),
        new KeyValue("0", TYPE_KEYBOARD, 0x27, 0, 0),
        // 功能键
        new KeyValue("Enter", TYPE_KEYBOARD, 0x28, 0, 0),
        new KeyValue("Escape", TYPE_KEYBOARD, 0x29, 0, 0),
        new KeyValue("Backspace", TYPE_KEYBOARD, 0x2A, 0, 0),
        new KeyValue("Tab", TYPE_KEYBOARD, 0x2B, 0, 0),
        new KeyValue("Space", TYPE_KEYBOARD, 0x2C, 0, 0),
        new KeyValue("Delete", TYPE_KEYBOARD, 0x4C, 0, 0),
        new KeyValue("Home", TYPE_KEYBOARD, 0x4A, 0, 0),
        new KeyValue("End", TYPE_KEYBOARD, 0x4D, 0, 0),
        new KeyValue("↑", TYPE_KEYBOARD, 0x52, 0, 0),
        new KeyValue("↓", TYPE_KEYBOARD, 0x51, 0, 0),
        new KeyValue("←", TYPE_KEYBOARD, 0x50, 0, 0),
        new KeyValue("→", TYPE_KEYBOARD, 0x4F, 0, 0),
        new KeyValue("F1", TYPE_KEYBOARD, 0x3A, 0, 0),
        new KeyValue("F2", TYPE_KEYBOARD, 0x3B, 0, 0),
        new KeyValue("F3", TYPE_KEYBOARD, 0x3C, 0, 0),
        new KeyValue("F4", TYPE_KEYBOARD, 0x3D, 0, 0),
        new KeyValue("F5", TYPE_KEYBOARD, 0x3E, 0, 0),
        new KeyValue("F6", TYPE_KEYBOARD, 0x3F, 0, 0),
        new KeyValue("F7", TYPE_KEYBOARD, 0x40, 0, 0),
        new KeyValue("F8", TYPE_KEYBOARD, 0x41, 0, 0),
        new KeyValue("F9", TYPE_KEYBOARD, 0x42, 0, 0),
        new KeyValue("F10", TYPE_KEYBOARD, 0x43, 0, 0),
        new KeyValue("F11", TYPE_KEYBOARD, 0x44, 0, 0),
        new KeyValue("F12", TYPE_KEYBOARD, 0x45, 0, 0),
        // 组合键
        new KeyValue("Ctrl+C", TYPE_KEYBOARD, 0x06, 0, 0x01),
        new KeyValue("Ctrl+V", TYPE_KEYBOARD, 0x19, 0, 0x01),
        new KeyValue("Ctrl+X", TYPE_KEYBOARD, 0x1B, 0, 0x01),
        new KeyValue("Ctrl+Z", TYPE_KEYBOARD, 0x1D, 0, 0x01),
        new KeyValue("Ctrl+A", TYPE_KEYBOARD, 0x04, 0, 0x01),
        new KeyValue("Ctrl+S", TYPE_KEYBOARD, 0x16, 0, 0x01),
        new KeyValue("Alt+F4", TYPE_KEYBOARD, 0x3D, 0, 0x04),
        new KeyValue("Alt+Tab", TYPE_KEYBOARD, 0x2B, 0, 0x04),
        new KeyValue("Win+D", TYPE_KEYBOARD, 0x07, 0, 0x08),
        new KeyValue("Win+L", TYPE_KEYBOARD, 0x0F, 0, 0x08),
        // 多媒体
        new KeyValue("播放/暂停", TYPE_CONSUMER, 0xCD, 0x00, 0),
        new KeyValue("下一曲", TYPE_CONSUMER, 0xB5, 0x00, 0),
        new KeyValue("上一曲", TYPE_CONSUMER, 0xB6, 0x00, 0),
        new KeyValue("音量+", TYPE_CONSUMER, 0xE9, 0x00, 0),
        new KeyValue("音量-", TYPE_CONSUMER, 0xEA, 0x00, 0),
        new KeyValue("静音", TYPE_CONSUMER, 0xE2, 0x00, 0),
        new KeyValue("计算器", TYPE_CONSUMER, 0x92, 0x01, 0),
    };
}
