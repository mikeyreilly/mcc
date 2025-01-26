package com.quaxt.mcc.asm;

import com.quaxt.mcc.tacky.Jump;
import com.quaxt.mcc.tacky.JumpIfNotZero;
import com.quaxt.mcc.tacky.JumpIfZero;
import com.quaxt.mcc.tacky.LabelIr;

public sealed interface Instruction permits AllocateStack, Binary, Call, Cmp, DeallocateStack, JmpCC, Mov, Nullary, Push, SetCC, Unary, Jump, LabelIr {


}
