package com.quaxt.mcc.tacky;

public sealed interface InstructionIr permits BinaryIr, Copy, Jump, JumpIfNotZero, JumpIfZero, LabelIr, ReturnInstructionIr, UnaryIr {
}
