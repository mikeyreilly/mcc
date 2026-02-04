package com.quaxt.mcc.tacky;

import com.quaxt.mcc.asm.Instruction;

public record Pos(int pos) implements InstructionIr, Instruction {
}
