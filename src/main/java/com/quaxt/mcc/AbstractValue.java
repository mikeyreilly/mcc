package com.quaxt.mcc;

import com.quaxt.mcc.asm.Operand;
import com.quaxt.mcc.tacky.ValIr;

public sealed interface AbstractValue permits Operand, ValIr {}
