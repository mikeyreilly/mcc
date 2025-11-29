package com.quaxt.mcc.tacky;

import com.quaxt.mcc.AbstractInstruction;
import com.quaxt.mcc.Ignore;
import com.quaxt.mcc.asm.Nullary;

public sealed interface InstructionIr extends AbstractInstruction permits Ignore,
        Nullary,
        AddPtr,
        AtomicStore,
        BinaryIr,
        BinaryWithOverflowIr,
        BuiltinC23VaStartIr,
        BuiltinVaArgIr,
        Compare,
        Copy,
        CopyBitsFromOffset,
        CopyBitsToOffset,
        CopyFromOffset,
        CopyToOffset,
        DoubleToInt,
        DoubleToUInt,
        FunCall,
        GetAddress,
        IntToDouble,
        Jump,
        JumpIfNotZero,
        JumpIfZero,
        LabelIr,
        Load,
        ReturnIr,
        SignExtendIr,
        Store,
        TruncateIr,
        UIntToDouble,
        UnaryIr,
        ZeroExtendIr {
}
