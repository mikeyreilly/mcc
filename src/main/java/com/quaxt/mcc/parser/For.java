package com.quaxt.mcc.parser;

public record For(ForInit init, Exp condition, Exp post, Statement body, String label) implements Statement {
}
