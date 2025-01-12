package com.quaxt.mcc.parser;

public record Assignment(Exp left, Exp right) implements Exp {
}
