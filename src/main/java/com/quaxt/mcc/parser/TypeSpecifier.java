package com.quaxt.mcc.parser;

public sealed interface TypeSpecifier extends Parser.DeclarationSpecifier permits PrimitiveTypeSpecifier, StructOrUnionSpecifier, TypedefName, EnumSpecifier {}
