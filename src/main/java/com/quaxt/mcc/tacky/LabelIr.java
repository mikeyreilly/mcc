package com.quaxt.mcc.tacky;
import com.quaxt.mcc.asm.Instruction;
public record LabelIr(String label) implements InstructionIr, Instruction {
}
