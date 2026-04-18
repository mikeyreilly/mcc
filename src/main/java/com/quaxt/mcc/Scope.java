package com.quaxt.mcc;

import com.quaxt.mcc.asm.Todo;
import com.quaxt.mcc.semantic.SemanticAnalysis;

import java.util.HashMap;
import java.util.Map;

public class Scope {
    Scope parent;
    Map<String, SemanticAnalysis.Entry> entryMap;

    public Scope(Scope parent) {
        this.parent=parent;
        this.entryMap= new HashMap<>();
    }

    public Scope() {
        this(null);
    }

    public void put(String name, SemanticAnalysis.Entry entry) {
        entryMap.put(name, entry);
    }

    public Pair<SemanticAnalysis.Entry,Boolean> getEntryAndCurrentScope(String name) {
        var e = entryMap.get(name);
        if (e != null) {
            return new Pair<>(e, true);
        }
        var s = parent;
        while (s != null) {
            e = s.entryMap.get(name);
            if (e != null) {
                return new Pair<>(e, false);
            }
            s = s.parent;
        }
        return null;
    }

    public Scope childScope() {
        return new Scope(this);
    }
}
