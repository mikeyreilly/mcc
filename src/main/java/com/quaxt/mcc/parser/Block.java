package com.quaxt.mcc.parser;

import java.util.List;

public record Block(List<BlockItem> blockItems) implements Statement {
}
