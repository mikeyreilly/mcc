package com.quaxt.mcc.parser;

import java.util.ArrayList;

public record StructDecl(boolean isUnion,
                         String tag,
                         ArrayList<MemberDeclaration> members) implements Declaration {
}
