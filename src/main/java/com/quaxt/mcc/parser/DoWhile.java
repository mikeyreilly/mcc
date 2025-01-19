package com.quaxt.mcc.parser;


public record DoWhile(Statement body, Exp condition, String label) implements Statement {
}
