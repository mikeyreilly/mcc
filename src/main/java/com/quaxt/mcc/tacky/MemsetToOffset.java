package com.quaxt.mcc.tacky;

public record MemsetToOffset(VarIr dst, long offset, int c, long byteCount) implements InstructionIr {
//memset(void *s, int c, size_t n);
}
