package com.quaxt.mcc.tacky;

public record Load(ValIr ptr, VarIr dst) implements InstructionIr {
    public Load(ValIr ptr, VarIr dst){
        this.ptr=ptr;
        this.dst=dst;
    }
}
