package com.quaxt.mcc.asm;

import com.quaxt.mcc.tacky.Jump;
import com.quaxt.mcc.tacky.JumpIfNotZero;
import com.quaxt.mcc.tacky.JumpIfZero;
import com.quaxt.mcc.tacky.LabelIr;

public sealed interface Instruction permits AllocateStack, Binary, Cmp, Mov, Nullary, SetCC, Unary, Jump, JmpCC, LabelIr {


}
