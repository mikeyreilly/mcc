package com.quaxt.mcc;

import java.util.ArrayList;

public record StructEntry(int alignment, int size,
                          ArrayList<MemberEntry> members) {
    public MemberEntry findMember(String member) {
        for (MemberEntry me : members) {
            if (me.name().equals(member)) return me;
        }
        return null;
    }
}
