package com.quaxt.mcc.parser;

import com.quaxt.mcc.semantic.Type;

public record AddrOf(Exp exp, Type type) implements Exp {
}