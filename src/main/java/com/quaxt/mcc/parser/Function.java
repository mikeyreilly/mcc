package com.quaxt.mcc.parser;

import com.quaxt.mcc.Token;

import java.util.List;

public record Function(String name, Token returnType, List<BlockItem> blockItems) {
}

