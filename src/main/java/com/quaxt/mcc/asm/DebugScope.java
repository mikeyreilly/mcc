package com.quaxt.mcc.asm;

public record DebugScope(int id, int parentId, String startLabel,
                         String endLabel) {
}
