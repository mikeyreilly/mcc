package com.quaxt.mcc.semantic;

import com.quaxt.mcc.parser.Constant;

public record Array(Type element, Constant arraySize) implements Type {}
