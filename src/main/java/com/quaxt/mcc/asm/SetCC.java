package com.quaxt.mcc.asm;

import com.quaxt.mcc.CmpOperator;

public record SetCC(CmpOperator cmpOperator, boolean unsigned,
                    Operand operand) implements Instruction {
}
