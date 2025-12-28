package com.quaxt.mcc.tacky;

public record AddPtr(VarIr ptr, ValIr index, int scale, VarIr dst)
        implements InstructionIr {
    public AddPtr(VarIr ptr, ValIr index, int scale, VarIr dst) {
        this.ptr=ptr;
        this.index=index;
        this.scale=scale;
        this.dst=dst;
    }
}
