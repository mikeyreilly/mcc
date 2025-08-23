package com.quaxt.mcc.semantic;

import com.quaxt.mcc.parser.Constant;

public record Array(Type element, Constant arraySize) implements Type {
    public Array(Type element, Constant arraySize){
        this.element=element;
        this.arraySize=arraySize;
    }
    @Override
    public boolean looseEquals(Type other) {
        if (other instanceof Array(Type otherElement, Constant otherArraySize)) {
            return otherElement.looseEquals(otherElement) && (arraySize == null || otherArraySize == null || arraySize.toLong() == otherArraySize.toLong());
        }
        return false;
    }
}
