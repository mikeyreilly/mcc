package com.quaxt.mcc.parser;

sealed public interface Statement extends BlockItem permits Block, Compound, Exp, If, NullStatement, Return {
}

