package com.quaxt.mcc.tacky;

import com.quaxt.mcc.parser.Var;

import java.util.List;

public record FunctionIr(String name, boolean global, List<Var> type,
                         List<InstructionIr> instructions) implements TopLevel {
}
