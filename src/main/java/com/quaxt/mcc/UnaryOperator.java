package com.quaxt.mcc;

public enum UnaryOperator {
    DIV, IDIV,
    BITWISE_NOT, // ~
    UNARY_MINUS, // the book calls this negation
    NOT, // logical not !
    UNARY_SHR,
    POST_INCREMENT,
    POST_DECREMENT,
    CLZ, // the number of leading 0-bits - only allowed in UnaryIr
    BSWAP
}
