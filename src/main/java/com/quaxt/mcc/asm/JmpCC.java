package com.quaxt.mcc.asm;

import com.quaxt.mcc.CmpOperator;

public record JmpCC(CC cc, String label) implements Instruction {
    public static JmpCC newJmpCC(CmpOperator cmpOperator, boolean unsigned,
                                 String label){
        return new OldJmpCC(cmpOperator, unsigned,label).toJmpCC2();
    }
}
