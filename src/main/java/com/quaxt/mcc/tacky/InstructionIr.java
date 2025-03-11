package com.quaxt.mcc.tacky;

public sealed interface InstructionIr permits AddPtr, BinaryIr, Copy, CopyToOffset, DoubleToInt, DoubleToUInt, FunCall, GetAddress, IntToDouble, Jump, JumpIfNotZero, JumpIfZero, LabelIr, Load, ReturnInstructionIr, SignExtendIr, Store, TruncateIr, UIntToDouble, UnaryIr, ZeroExtendIr {
}
