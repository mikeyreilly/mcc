package com.quaxt.mcc.tacky;

public sealed interface InstructionIr permits BinaryIr, Copy, FunCall, Jump, JumpIfNotZero, JumpIfZero, LabelIr, ReturnInstructionIr, SignExtendIr, TruncateIr, UnaryIr, ZeroExtendIr {
}
