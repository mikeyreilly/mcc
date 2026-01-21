package com.quaxt.mcc;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

public record StructDef(boolean isUnion, int alignment, int size,
                        ArrayList<MemberEntry> members) {
    public StructDef(boolean isUnion,
                     int alignment,
                     int size,
                     ArrayList<MemberEntry> members) {
        this.isUnion = isUnion;
        this.alignment = alignment;
        this.size = size;
        this.members = members;
    }
    public MemberEntry findMember(String member) {
        for (MemberEntry me : members) {
            if (me.name() != null && me.name().equals(member)) return me;
        }
        return null;
    }

    public List<MemberEntry> namedMembers() {
        return new AbstractList() {
            @Override
            public int size() {
                int i = 0;
                for (var m : members) {
                    if (m.name() != null) {
                        i++;
                    }
                }
                return i;
            }

            @Override
            public Object get(int index) {
                int i = 0;
                for (var m : members) {
                    if (m.name() != null) {
                        if (i == index) {
                            return m;
                        }
                        i++;
                    }

                }
                throw new IndexOutOfBoundsException();
            }
        };
    }
}
