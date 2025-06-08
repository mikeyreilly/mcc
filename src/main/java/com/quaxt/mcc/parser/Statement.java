package com.quaxt.mcc.parser;

sealed public interface Statement
    extends BlockItem permits Block, Break, CaseStatement, Continue, DoWhile, Exp, For, Goto, If, LabelledStatement, NullStatement, Return, Switch, While {
}
