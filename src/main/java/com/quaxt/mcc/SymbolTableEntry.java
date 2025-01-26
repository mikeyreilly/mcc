package com.quaxt.mcc;

import com.quaxt.mcc.semantic.Type;

public record SymbolTableEntry(Type type, boolean alreadyDefined) {
}
