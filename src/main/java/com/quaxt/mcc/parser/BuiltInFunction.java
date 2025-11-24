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
    },
    BUILTIN_ADD_OVERFLOW("__builtin_add_overflow") {
        public Type determineReturnType(List<Exp> args) {
            return Primitive.BOOL;
        }
    },

    BUILTIN_SUB_OVERFLOW("__builtin_sub_overflow") {
        public Type determineReturnType(List<Exp> args) {
            return Primitive.BOOL;
        }
    },
    BUILTIN_MUL_OVERFLOW("__builtin_mul_overflow") {
        public Type determineReturnType(List<Exp> args) {
            return Primitive.BOOL;
        }
    },
    BUILTIN_BSWAP64("__builtin_bswap64"){
        public Type determineReturnType(List<Exp> args) {
            return Primitive.ULONG;
        }

        public Type getParamType(int i) {
            return Primitive.ULONG;
        }
    },
    BUILTIN_BSWAP32("__builtin_bswap32"){
        public Type determineReturnType(List<Exp> args) {
            return Primitive.UINT;
        }

        public Type getParamType(int i) {
            return Primitive.UINT;
        }
    }, BUILTIN_BSWAP16("__builtin_bswap16") {
        public Type determineReturnType(List<Exp> args) {
            return Primitive.USHORT;
        }

        public Type getParamType(int i) {
            return Primitive.USHORT;
        }
    }, SYNC_SYNCHRONIZE("__sync_synchronize") {
        public Type determineReturnType(List<Exp> args) {
            return Primitive.VOID;
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
        if (this == ATOMIC_STORE_N || this == BUILTIN_ADD_OVERFLOW|| this == BUILTIN_SUB_OVERFLOW||this == BUILTIN_MUL_OVERFLOW) return 3;
        if (this == BUILTIN_BSWAP64) return 1;
        return 2;
    }

    public Type getParamType(int i) {
        return null;
    }
}
