package com.quaxt.mcc.asm;

import com.quaxt.mcc.CmpOperator;

public record SetCC(CmpOperator cmpOperator,
                    Operand operand) implements Instruction {
}
