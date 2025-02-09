package com.quaxt.mcc.asm;

import com.quaxt.mcc.tacky.Jump;
import com.quaxt.mcc.tacky.JumpIfNotZero;
import com.quaxt.mcc.tacky.JumpIfZero;
import com.quaxt.mcc.tacky.LabelIr;

public sealed interface Instruction permits  Binary, Call, Cdq, Cmp, JmpCC, Mov, Movsx, Nullary, Push, SetCC, Unary, Jump, LabelIr {


}
