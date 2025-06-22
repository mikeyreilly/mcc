package com.quaxt.mcc.asm;

import java.util.Objects;

public final class Pseudo implements Reg {
    public final String identifier;
    public final TypeAsm type;
    public final boolean isStatic;
    public final boolean isAliased;

    public Pseudo(String identifier, TypeAsm type, boolean isStatic, boolean isAliased) {
        this.identifier = identifier;
        this.type = type;
        this.isStatic = isStatic;
        this.isAliased = isAliased;
    }

    @Override
    public String toString() {
        return "Pseudo{" + "identifier='" + identifier + '\'' + ", type=" + type + ", isStatic=" + isStatic + ", isAliased=" + isAliased + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Pseudo pseudo)) return false;
        return isStatic == pseudo.isStatic && Objects.equals(identifier, pseudo.identifier) && Objects.equals(type, pseudo.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier, type, isStatic);
    }

}
