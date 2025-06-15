package com.quaxt.mcc.parser;

public sealed interface Declaration extends BlockItem permits Function, StructOrUnionSpecifier, VarDecl {
}
