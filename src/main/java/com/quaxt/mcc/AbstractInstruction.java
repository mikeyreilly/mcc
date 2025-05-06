package com.quaxt.mcc;

import com.quaxt.mcc.asm.Instruction;
import com.quaxt.mcc.tacky.InstructionIr;

public sealed interface AbstractInstruction permits InstructionIr, Instruction {}
