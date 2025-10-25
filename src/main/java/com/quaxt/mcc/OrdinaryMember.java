package com.quaxt.mcc;

import com.quaxt.mcc.semantic.Type;

public record OrdinaryMember(String name, Type type, int byteOffset)
        implements MemberEntry {}
