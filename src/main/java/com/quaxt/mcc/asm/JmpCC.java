package com.quaxt.mcc.asm;

import com.quaxt.mcc.CmpOperator;

public record JmpCC(CmpOperator cmpOperator, boolean unsigned,
                    String label) implements Instruction {
}
