package com.quaxt.mcc.parser;

import com.quaxt.mcc.semantic.Type;

public record MemberDeclaration(Type type, String name, StructOrUnionSpecifier structOrUnionSpecifier) {}
