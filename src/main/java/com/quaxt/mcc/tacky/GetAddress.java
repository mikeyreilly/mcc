package com.quaxt.mcc.tacky;

public record GetAddress(ValIr obj, VarIr dst) implements InstructionIr {
    public GetAddress(ValIr obj, VarIr dst) {
        this.obj = obj;
        this.dst = dst;
    }
}
