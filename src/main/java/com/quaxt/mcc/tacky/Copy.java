package com.quaxt.mcc.tacky;

public record Copy(ValIr val, VarIr dst) implements InstructionIr {
    public Copy(ValIr val, VarIr dst){
        this.val=val;
        this.dst=dst;
    }
}
