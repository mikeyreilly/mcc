package com.quaxt.mcc.tacky;

import com.quaxt.mcc.Mcc;
import com.quaxt.mcc.semantic.Pointer;

public record CopyBitsFromOffset(VarIr base, int byteOffset, int bitOffset,
                                 int bitWidth,
                                 VarIr dst) implements InstructionIr {
    public CopyBitsFromOffset(VarIr base, int byteOffset, int bitOffset,
                       int bitWidth,
                       VarIr dst){
        assert !(Mcc.type(base) instanceof Pointer);
        this.base=base;
        this.byteOffset=byteOffset;
        this.bitOffset=bitOffset;
        this.bitWidth=bitWidth;
        this.dst=dst;

    }
}
