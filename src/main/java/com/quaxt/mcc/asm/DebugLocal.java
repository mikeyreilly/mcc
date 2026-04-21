package com.quaxt.mcc.asm;

public record DebugLocal(String internalName, String displayName, int scopeId,
                         boolean parameter) {
}
