package com.quaxt.mcc.semantic;

import com.quaxt.mcc.StructDef;

/* MR-TODO remove unused field structDef if I still don't need it after adding support for structs with anonymous struct/union members*/
public record Structure(boolean isUnion, String tag, StructDef structDef) implements Type {
    public Structure(boolean isUnion, String tag, StructDef structDef) {
        this.isUnion = isUnion;
        this.tag = tag;
        this.structDef = structDef;
    }
    public boolean looseEquals(Type other) {
        return this.equals(other);
    }

}
