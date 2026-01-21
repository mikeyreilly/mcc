package com.quaxt.mcc;

import com.quaxt.mcc.semantic.Type;

public record OrdinaryMember(String name, Type type, int byteOffset, int alignment)
        implements MemberEntry {
    @Override
    public Type internalType() {
        return type;
    }
}
