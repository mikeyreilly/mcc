package com.quaxt.mcc;

import com.quaxt.mcc.semantic.Type;

public sealed interface MemberEntry permits BitFieldMember, OrdinaryMember {
    String name();
    Type type();
    int byteOffset();

    Type internalType();
}
