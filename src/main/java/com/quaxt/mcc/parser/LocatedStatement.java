package com.quaxt.mcc.parser;

public record LocatedStatement(Statement statement, int pos) implements Statement {
}
