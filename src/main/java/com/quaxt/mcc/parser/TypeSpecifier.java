package com.quaxt.mcc.parser;

public sealed interface TypeSpecifier extends DeclarationSpecifier permits PrimitiveTypeSpecifier, StructOrUnionSpecifier, TypedefName {}
