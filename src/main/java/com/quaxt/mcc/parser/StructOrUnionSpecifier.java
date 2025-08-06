package com.quaxt.mcc.parser;

import java.util.ArrayList;

public record StructOrUnionSpecifier(boolean isUnion,
                                     String tag,
                                     ArrayList<MemberDeclaration> members) implements Declaration, TypeSpecifier {
    public StructOrUnionSpecifier(boolean isUnion,
    String tag,
    ArrayList<MemberDeclaration> members){
        this.isUnion=isUnion;
        this.tag=tag;
        this.members=members;
    }
}
