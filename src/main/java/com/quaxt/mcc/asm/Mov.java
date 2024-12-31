package com.quaxt.mcc.asm;

import java.io.PrintWriter;

public record Mov(Operand src, Operand dst) implements Instruction {

    @Override
    public void emitAsm(PrintWriter out) {
       // out.println("        movl	$" + i + ", %eax");
        throw new RuntimeException("Not implemented");
    }
}
