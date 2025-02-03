package com.quaxt.mcc.parser;

import com.quaxt.mcc.semantic.Type;

public record Assignment(Exp left, Exp right, Type type) implements Exp {
}
