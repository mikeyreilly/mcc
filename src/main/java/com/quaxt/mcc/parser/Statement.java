package com.quaxt.mcc.parser;

sealed public interface Statement
    extends BlockItem permits Block, Break, Continue, Compound, DoWhile, Exp,
            For, If, NullStatement, Return, While {
}
