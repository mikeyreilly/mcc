package com.quaxt.mcc.parser;

public sealed interface TypeSpecifier extends Parser.DeclarationSpecifier permits EnumSpecifier, PrimitiveTypeSpecifier, StructOrUnionSpecifier, TypedefName, Typeof, TypeofT {}
