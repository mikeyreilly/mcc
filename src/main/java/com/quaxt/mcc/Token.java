package com.quaxt.mcc;

import java.util.regex.Pattern;

public sealed interface Token permits BinaryOperator, TokenType, TokenWithValue {
    default Token type(){
        return this;
    }

   default Pattern regex(){
        throw new IllegalArgumentException("can't do this");
   }
}
