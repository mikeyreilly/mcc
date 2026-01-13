package com.quaxt.mcc.tacky;

public record Memset(VarIr dst, int c, long byteCount) implements InstructionIr {
//memset(void *s, int c, size_t n);
}
