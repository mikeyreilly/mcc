package com.quaxt.mcc.atomics;

import com.quaxt.mcc.asm.Todo;
import com.quaxt.mcc.parser.Constant;
import com.quaxt.mcc.tacky.ValIr;

public enum MemoryOrder {
    RELAXED(0), CONSUME(1), ACQUIRE(2), RELEASE(3), ACQ_REL(4), SEQ_CST(5);

    private int value;

    MemoryOrder(int value) {
        this.value = value;
    }

    int value() {
        return value;
    }

    public static MemoryOrder from(int v) {
        return switch (v) {
            case 0 -> RELAXED;
            case 1 -> CONSUME;
            case 2 -> ACQUIRE;
            case 3 -> RELEASE;
            case 4 -> ACQ_REL;
            case 5 -> SEQ_CST;
            default ->SEQ_CST;
        };
    }

    public static MemoryOrder from(ValIr v) {
        if (v instanceof Constant c) {
            long l = c.toLong();
            return from((int) l);
        }
        throw new Todo();
    }
}
