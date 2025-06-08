package com.quaxt.mcc.parser;

public record CaseStatement(Switch enclosingSwitch, Constant<?> label, Statement statement) implements Statement {}
