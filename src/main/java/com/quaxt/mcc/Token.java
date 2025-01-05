package com.quaxt.mcc;

public sealed interface Token permits TokenWithValue, TokenType {
    default Token type(){
        return this;
    }
}
