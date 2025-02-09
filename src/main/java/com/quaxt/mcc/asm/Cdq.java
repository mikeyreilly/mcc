package com.quaxt.mcc.asm;

import static com.quaxt.mcc.asm.TypeAsm.QUADWORD;

public record Cdq(TypeAsm type) implements Instruction {
    @Override
    public String format(TypeAsm t) {
        return t == QUADWORD ? "cqo" : "cdq";
    }
}
