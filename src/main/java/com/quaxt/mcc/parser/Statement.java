package com.quaxt.mcc.parser;

sealed public interface Statement extends BlockItem permits Exp, NullStatement, Return {
}

