package com.quaxt.mcc.parser;

import com.quaxt.mcc.asm.Todo;
import com.quaxt.mcc.semantic.Pointer;
import com.quaxt.mcc.semantic.Primitive;
import com.quaxt.mcc.semantic.Type;

import java.util.List;

public enum BuiltInFunction {
    ATOMIC_STORE_N("__atomic_store_n", 3){
        public Type determineReturnType(List<Exp> args) {
            return Primitive.VOID;
        }
    }, ATOMIC_LOAD_N("__atomic_load_n", 2) {
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
    BUILTIN_ADD_OVERFLOW("__builtin_add_overflow", 3) {
        public Type determineReturnType(List<Exp> args) {
            return Primitive.BOOL;
        }
    },

    BUILTIN_SUB_OVERFLOW("__builtin_sub_overflow", 3) {
        public Type determineReturnType(List<Exp> args) {
            return Primitive.BOOL;
        }
    },
    BUILTIN_MUL_OVERFLOW("__builtin_mul_overflow", 3) {
        public Type determineReturnType(List<Exp> args) {
            return Primitive.BOOL;
        }
    },
    BUILTIN_CLZLL("__builtin_clzll", 1){
        public Type determineReturnType(List<Exp> args) {
            return Primitive.ULONGLONG;
        }

        public Type getParamType(int i) {
            return Primitive.ULONGLONG;
        }
    },

    BUILTIN_BSWAP64("__builtin_bswap64", 1){
        public Type determineReturnType(List<Exp> args) {
            return Primitive.ULONG;
        }

        public Type getParamType(int i) {
            return Primitive.ULONG;
        }
    },
    BUILTIN_BSWAP32("__builtin_bswap32", 1){
        public Type determineReturnType(List<Exp> args) {
            return Primitive.UINT;
        }

        public Type getParamType(int i) {
            return Primitive.UINT;
        }
    }, BUILTIN_BSWAP16("__builtin_bswap16", 1) {
        public Type determineReturnType(List<Exp> args) {
            return Primitive.USHORT;
        }

        public Type getParamType(int i) {
            return Primitive.USHORT;
        }
    }, SYNC_SYNCHRONIZE("__sync_synchronize", 0) {
        public Type determineReturnType(List<Exp> args) {
            return Primitive.VOID;
        }
    }, BUILTIN_NANF("__builtin_nanf", 1) {
        public Type determineReturnType(List<Exp> args) {
            return Primitive.FLOAT;
        }

        public Type getParamType(int i) {
            return new Pointer(Primitive.CHAR);
        }
    }, BUILTIN_INFF("__builtin_inff", 0) {
        public Type determineReturnType(List<Exp> args) {
            return Primitive.FLOAT;
        }

        public Type getParamType(int i) {
            return new Pointer(Primitive.CHAR);
        }
    };

    private final String identifier;
    private final int paramsSize;

    BuiltInFunction(String identifier, int paramSize) {
        this.identifier = identifier;
        this.paramsSize = paramSize;
    }

    static BuiltInFunction fromIdentifier(String identifier) {
        for (BuiltInFunction v : BuiltInFunction.class.getEnumConstants()) {
            if (v.identifier.equals(identifier)) return v;
        }
        return null;
    }

    public abstract Type determineReturnType(List<Exp> args);

    public int paramsSize() {
        return paramsSize;
    }


    public Type getParamType(int i) {
        return null;
    }
}
