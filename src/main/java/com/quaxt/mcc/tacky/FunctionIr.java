package com.quaxt.mcc.tacky;

import com.quaxt.mcc.parser.Identifier;

import java.util.List;

public record FunctionIr(String name, boolean global, List<Identifier> type,
                         List<InstructionIr> instructions) implements TopLevel {
}
