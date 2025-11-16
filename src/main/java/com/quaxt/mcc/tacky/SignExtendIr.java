package com.quaxt.mcc.tacky;

public record SignExtendIr(ValIr src, VarIr dst) implements InstructionIr {
    public SignExtendIr(ValIr src, VarIr dst){
        this.src=src;
        this.dst=dst;
        dst=dst;
        }
}
