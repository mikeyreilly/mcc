package com.quaxt.mcc.asm;

import static com.quaxt.mcc.asm.PrimitiveTypeAsm.BYTE;
import static com.quaxt.mcc.asm.PrimitiveTypeAsm.LONGWORD;

public record Imm(long i) implements Operand {
    /**
     * It's awkward if it doesn't fit in a signed int
     */
    public boolean isAwkward() {
        int ii = (int) i;
        return (long) ii != i;
    }

    public Operand truncate(TypeAsm typeAsm) {
        switch (typeAsm) {
            case BYTE -> {
                if (i <= 255 && i > Byte.MIN_VALUE) return this;
                return new Imm(i & 0xff);
            }
            case LONGWORD -> {
                if (i <= 0xFFFF_FFFFL && i > Integer.MIN_VALUE) return this;
                return new Imm(i & 0xFFFF_FFFFL);

            }
            default -> {return this;}
        }
    }
}