package com.quaxt.mcc.parser;

public record For(ForInit init, Exp condition, Exp post, Statement body) implements Statement {
}
