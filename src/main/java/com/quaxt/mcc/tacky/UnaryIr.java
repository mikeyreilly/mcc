package com.quaxt.mcc.tacky;

import com.quaxt.mcc.UnaryOperator;

public record UnaryIr(UnaryOperator op, ValIr src, VarIr dst) implements InstructionIr {

}
