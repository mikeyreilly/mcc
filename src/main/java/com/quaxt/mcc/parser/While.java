package com.quaxt.mcc.parser;


public record While(Exp condition, Statement body, String label) implements Statement {
}
