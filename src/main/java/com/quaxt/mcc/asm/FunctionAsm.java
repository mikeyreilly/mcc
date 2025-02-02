package com.quaxt.mcc.asm;

import java.util.List;

public record FunctionAsm(String name, boolean global, List<Instruction> instructions) implements AsmNode, TopLevelAsm {
}

