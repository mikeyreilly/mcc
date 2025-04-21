package com.quaxt.mcc.tacky;

public record CopyToOffset(ValIr src, VarIr dst, long offset) implements InstructionIr {
    public CopyToOffset(ValIr src, VarIr dst, long offset){
        this.src=src;
        this.dst=dst;
        this.offset=offset;
    }
}
