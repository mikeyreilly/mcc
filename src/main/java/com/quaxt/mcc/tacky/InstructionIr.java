package com.quaxt.mcc.tacky;

import com.quaxt.mcc.AbstractInstruction;
import com.quaxt.mcc.Ignore;

public sealed interface InstructionIr extends AbstractInstruction permits Ignore, AddPtr, BinaryIr, Copy, CopyFromOffset, CopyToOffset, DoubleToInt, DoubleToUInt, FunCall, GetAddress, IntToDouble, Jump, JumpIfNotZero, JumpIfZero, LabelIr, Load, ReturnIr, SignExtendIr, Store, TruncateIr, UIntToDouble, UnaryIr, ZeroExtendIr {
}
