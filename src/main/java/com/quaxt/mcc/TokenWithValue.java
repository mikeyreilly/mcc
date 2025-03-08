package com.quaxt.mcc;

import java.util.regex.Pattern;

public record TokenWithValue(Token type,
                             String value) implements Token {
    public TokenWithValue(Token type,
                   String value){
        this.type=type;
        this.value=value;
    }
    @Override
    public Pattern regex() {
        throw new UnsupportedOperationException();
    }

    public String toString() {
        return value;
    }
}
