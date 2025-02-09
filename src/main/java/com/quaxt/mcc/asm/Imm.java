package com.quaxt.mcc.asm;

public record Imm(long i) implements Operand {
    /**
     * It's awkward if it doesn't fit in a signed int
     */
    public boolean isAwkward() {
        int ii = (int) i;
        return (long) ii != i;
    }
}
