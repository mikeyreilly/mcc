package com.quaxt.mcc.parser.parser2;

public sealed interface TypeSpecifier extends DeclarationSpecifier permits PrimitiveTypeSpecifier, StructOrUnionSpecifier, TypedefName {}
