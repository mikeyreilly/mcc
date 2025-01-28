package com.quaxt.mcc.parser;

import java.util.List;

public record Function(String name,
                       List<Identifier> parameters,
                       Block body,
                       StorageClass storageClass) implements Declaration {
}
