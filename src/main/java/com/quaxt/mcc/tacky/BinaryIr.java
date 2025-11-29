package com.quaxt.mcc.tacky;

import com.quaxt.mcc.BinaryOperator;

public record BinaryIr(BinaryOperator op, ValIr v1, ValIr v2,
                       VarIr dstName) implements InstructionIr {
    public BinaryIr(BinaryOperator op, ValIr v1, ValIr v2,
                    VarIr dstName){
        this.op=op;
        this.v1=v1;
        this.v2=v2;
        this.dstName=dstName;
    }

}