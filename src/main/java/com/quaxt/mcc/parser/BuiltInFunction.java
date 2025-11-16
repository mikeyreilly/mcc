package com.quaxt.mcc.parser;

import com.quaxt.mcc.asm.Todo;
import com.quaxt.mcc.semantic.Pointer;
import com.quaxt.mcc.semantic.Primitive;
import com.quaxt.mcc.semantic.Type;

import java.util.List;

public enum BuiltInFunction {
    ATOMIC_STORE_N("__atomic_store_n"){
        public Type determineReturnType(List<Exp> args) {
            return Primitive.VOID;
        }
    }, ATOMIC_LOAD_N("__atomic_load_n") {
        @Override
        public Type determineReturnType(List<Exp> args) {
            Exp x = args.get(0);
            Type t = x.type();
            if (t instanceof Pointer(Type referenced)){
                return referenced;
            }
            throw new Todo();
        }
    };

    private final String identifier;

    BuiltInFunction(String identifier) {
        this.identifier = identifier;
    }

    static BuiltInFunction fromIdentifier(String identifier) {
        for (BuiltInFunction v : BuiltInFunction.class.getEnumConstants()) {
            if (v.identifier.equals(identifier)) return v;
        }
        return null;
    }

    public abstract Type determineReturnType(List<Exp> args);

    public int paramsSize() {
        return this == ATOMIC_STORE_N ? 3:2;
    }
}
