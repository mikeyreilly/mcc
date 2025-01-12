package com.quaxt.mcc.parser;

import java.util.Optional;

public record Declaration(String name,
                          Optional<Exp> init) implements BlockItem {
}

