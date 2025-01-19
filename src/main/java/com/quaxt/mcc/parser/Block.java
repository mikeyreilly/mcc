package com.quaxt.mcc.parser;

import java.util.ArrayList;

public record Block(ArrayList<BlockItem> blockItems) implements Statement {
}
