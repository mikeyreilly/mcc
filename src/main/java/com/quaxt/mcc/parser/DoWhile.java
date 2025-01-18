package com.quaxt.mcc.parser;


public record DoWhile(Statement body, Exp condition) implements Statement {
}
