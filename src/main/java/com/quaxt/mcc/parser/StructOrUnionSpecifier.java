package com.quaxt.mcc.parser;

import java.util.ArrayList;

public record StructOrUnionSpecifier(boolean isUnion,
                                     String tag,
                                     ArrayList<MemberDeclaration> members,
                                     boolean isAnonymous,
                                     Exp alignment) implements Declaration, TypeSpecifier {
    public StructOrUnionSpecifier withTag(String tag) {
        return new StructOrUnionSpecifier(isUnion, tag, members, isAnonymous, alignment);
    }
}
