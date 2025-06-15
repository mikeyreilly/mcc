package com.quaxt.mcc.parser.parser2;

import com.quaxt.mcc.parser.Declaration;
import com.quaxt.mcc.parser.MemberDeclaration;

import java.util.ArrayList;

public record StructOrUnionSpecifier(boolean isUnion,
                                     String tag,
                                     ArrayList<MemberDeclaration> members) implements Declaration, TypeSpecifier {
}
