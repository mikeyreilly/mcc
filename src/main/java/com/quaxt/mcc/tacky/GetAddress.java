package com.quaxt.mcc.tacky;

public record GetAddress(VarIr obj, VarIr dst) implements InstructionIr {
    public GetAddress(VarIr obj, VarIr dst) {
        this.obj = obj;
        this.dst = dst;
    }
}
