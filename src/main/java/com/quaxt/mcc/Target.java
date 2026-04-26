package com.quaxt.mcc;

import com.quaxt.mcc.semantic.Primitive;
import com.quaxt.mcc.semantic.Type;

import static com.quaxt.mcc.semantic.Primitive.*;

public enum Target {
    LINUX_GNU,
    WINDOWS_MSVC;

    public static Target hostDefault() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win") ? WINDOWS_MSVC : LINUX_GNU;
    }

    public static Target parse(String value) {
        return switch (value) {
            case "linux-gnu" -> LINUX_GNU;
            case "windows-msvc" -> WINDOWS_MSVC;
            default -> throw new IllegalArgumentException("unknown target: " + value);
        };
    }

    public boolean isWindowsMsvc() {
        return this == WINDOWS_MSVC;
    }

    public String executableSuffix() {
        return isWindowsMsvc() ? ".exe" : "";
    }

    public String objectSuffix() {
        return isWindowsMsvc() ? ".obj" : ".o";
    }

    public String assemblySuffix() {
        return isWindowsMsvc() ? ".asm" : ".s";
    }

    public long size(Type type) {
        if (type instanceof Primitive primitive) {
            return switch (primitive) {
                case BOOL, CHAR, UCHAR, SCHAR -> 1;
                case SHORT, USHORT -> 2;
                case INT, UINT, FLOAT -> 4;
                case LONG, ULONG -> isWindowsMsvc() ? 4 : 8;
                case LONGLONG, ULONGLONG, DOUBLE -> 8;
                case VOID -> 1;
            };
        }
        return -1;
    }

    public int alignment(Type type) {
        if (type instanceof Primitive primitive) {
            return switch (primitive) {
                case BOOL, CHAR, UCHAR, SCHAR, VOID -> 1;
                case SHORT, USHORT -> 2;
                case INT, UINT, FLOAT, LONG, ULONG -> 4;
                case LONGLONG, ULONGLONG, DOUBLE -> 8;
            };
        }
        return -1;
    }
}
