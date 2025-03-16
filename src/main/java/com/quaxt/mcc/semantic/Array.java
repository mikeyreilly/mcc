package com.quaxt.mcc.semantic;

import com.quaxt.mcc.parser.Constant;

public record Array(Type element, Constant arraySize) implements Type {
    @Override
    public boolean looseEquals(Type other) {
        return other instanceof Array(Type otherElement, Constant otherArraySize)
                && otherElement.looseEquals(otherElement) && arraySize.toInt()==otherArraySize.toInt();
    }
}
