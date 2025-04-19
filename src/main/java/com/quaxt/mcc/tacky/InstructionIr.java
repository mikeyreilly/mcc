package com.quaxt.mcc.tacky;

public sealed interface InstructionIr permits AddPtr, BinaryIr, Copy, CopyFromOffset, CopyToOffset, DoubleToInt, DoubleToUInt, FunCall, GetAddress, IntToDouble, Jump, JumpIfNotZero, JumpIfZero, LabelIr, Load, ReturnIr, SignExtendIr, Store, TruncateIr, UIntToDouble, UnaryIr, ZeroExtendIr {
}
