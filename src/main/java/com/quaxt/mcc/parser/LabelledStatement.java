package com.quaxt.mcc.parser;

public record LabelledStatement(String label, Statement statement) implements Statement {}
