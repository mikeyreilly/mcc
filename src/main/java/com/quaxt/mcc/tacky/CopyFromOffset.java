package com.quaxt.mcc.tacky;

import com.quaxt.mcc.Mcc;
import com.quaxt.mcc.semantic.Pointer;

public record CopyFromOffset(VarIr src, long offset, VarIr dst) implements InstructionIr {
    public CopyFromOffset(VarIr src, long offset, VarIr dst) {
        assert !(Mcc.type(src) instanceof Pointer);
        this.src = src;
        this.offset = offset;
        this.dst = dst;
    }
}
