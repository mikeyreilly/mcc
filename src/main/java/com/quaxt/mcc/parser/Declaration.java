package com.quaxt.mcc.parser;

import com.quaxt.mcc.parser.parser2.StructOrUnionSpecifier;

public sealed interface Declaration extends BlockItem permits Function, StructOrUnionSpecifier, VarDecl {
}
