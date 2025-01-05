package com.quaxt.mcc;

public record TokenWithValue(TokenType type,
                             String value) implements Token {
}
