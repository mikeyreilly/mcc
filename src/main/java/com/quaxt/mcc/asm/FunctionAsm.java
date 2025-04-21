package com.quaxt.mcc.asm;

import java.util.List;

public record FunctionAsm(String name, boolean global, boolean returnInMemory,
                          List<Instruction> instructions) implements AsmNode, TopLevelAsm {
}

