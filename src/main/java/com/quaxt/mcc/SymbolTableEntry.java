package com.quaxt.mcc;

import com.quaxt.mcc.semantic.Type;

import java.util.Objects;

public final class SymbolTableEntry {
    private final Type type;
    private final IdentifierAttributes attrs;
    public boolean aliased;

    public SymbolTableEntry(Type type, IdentifierAttributes attrs) {
        this.type = type;
        this.attrs = attrs;
    }

    public Type type() {return type;}

    public IdentifierAttributes attrs() {return attrs;}

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (SymbolTableEntry) obj;
        return Objects.equals(this.type, that.type) && Objects.equals(this.attrs, that.attrs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, attrs);
    }

    @Override
    public String toString() {
        return "SymbolTableEntry[" + "type=" + type + ", " + "attrs=" + attrs + ']';
    }

}
