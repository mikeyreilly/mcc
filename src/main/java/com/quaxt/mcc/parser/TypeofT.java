package com.quaxt.mcc.parser;

import com.quaxt.mcc.semantic.Type;

import static com.quaxt.mcc.semantic.Primitive.ULONG;

public record TypeofT(Type typeToSize) implements TypeSpecifier {

}
