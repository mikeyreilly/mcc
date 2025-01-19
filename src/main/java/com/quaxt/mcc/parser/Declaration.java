package com.quaxt.mcc.parser;

public record Declaration(String name,
                          Exp init) implements BlockItem, ForInit {
}

