package com.quaxt.mcc;

public record TokenWithValue(Token type,
                             String value) implements Token {
}
