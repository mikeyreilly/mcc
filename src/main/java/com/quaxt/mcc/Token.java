package com.quaxt.mcc;

import java.util.regex.Pattern;

public sealed interface Token permits BinaryOperator, TokenType, TokenWithValue {
    default Token type(){
        return this;
    }

    Pattern regex();
}
