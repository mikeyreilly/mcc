package com.quaxt.mcc.tacky;

import com.quaxt.mcc.Ignore;

public sealed interface InstructionIr permits Ignore, AddPtr, BinaryIr, Copy, CopyFromOffset, CopyToOffset, DoubleToInt, DoubleToUInt, FunCall, GetAddress, IntToDouble, Jump, JumpIfNotZero, JumpIfZero, LabelIr, Load, ReturnIr, SignExtendIr, Store, TruncateIr, UIntToDouble, UnaryIr, ZeroExtendIr {
}
