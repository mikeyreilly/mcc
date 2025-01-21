package com.quaxt.mcc.parser;

import java.util.List;

public record Function(String name,
                       List<Identifier> parameters,
                       Block block) implements Declaration {
}
