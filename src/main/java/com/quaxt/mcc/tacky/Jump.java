package com.quaxt.mcc.tacky;

import com.quaxt.mcc.asm.Instruction;

public record Jump(String label) implements InstructionIr, Instruction {
}
