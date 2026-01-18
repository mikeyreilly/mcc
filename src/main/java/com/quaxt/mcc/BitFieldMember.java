package com.quaxt.mcc;

import com.quaxt.mcc.semantic.Type;
import com.quaxt.mcc.semantic.WidthRestricted;

public record BitFieldMember(
        String name,
        Type type,
        int byteOffset,
        int bitOffset,
        int bitWidth
) implements MemberEntry {

    @Override
    public Type internalType() {
        return new WidthRestricted(type, bitWidth);
    }
}
