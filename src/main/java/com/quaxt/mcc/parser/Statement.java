package com.quaxt.mcc.parser;

sealed public interface Statement
    extends BlockItem permits LabelledStatement, Block, Break, Continue, DoWhile, Exp, For, Goto, If, NullStatement, Return, While {
}
