package com.quaxt.mcc.tacky;

public record Copy(ValIr src, VarIr dst) implements InstructionIr {
    public Copy(ValIr src, VarIr dst) {
        this.src = src;
        this.dst = dst;
    }

    public String toString() {
        return "Copy [" + dst + " = " + src + "]";
    }
}
