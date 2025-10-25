package com.quaxt.mcc;

import com.quaxt.mcc.semantic.Type;

public record BitFieldMember(
        String name,
        Type type,
        int byteOffset,
        int bitOffset,
        int bitWidth
) implements MemberEntry {}
