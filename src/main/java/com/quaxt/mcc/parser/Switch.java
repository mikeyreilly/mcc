package com.quaxt.mcc.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Switch implements Statement {
    public Exp exp;
    public Statement body;
    public String label;

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (Switch) obj;
        return Objects.equals(this.exp, that.exp) && Objects.equals(this.body
                , that.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(exp, body);
    }

    @Override
    public String toString() {
        return "Switch[" + "exp=" + exp + ", " + "body=" + body + ']';
    }

    public List<Constant> entries = new ArrayList<>();

    public void addEntry(Constant<?> c) {
        entries.add(c);
    }

    public String labelFor(Constant<?> c) {
        return label + (c == null ? "default" : '.' + Long.toUnsignedString(c.toLong()));
    }
}
