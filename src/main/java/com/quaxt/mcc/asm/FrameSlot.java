package com.quaxt.mcc.asm;

public record FrameSlot(long offset, int alignment) implements Operand {
    public FrameSlot(long offset) {
        this(offset, 0);
    }

    @Override
    public Operand plus(long offset) {
        return new FrameSlot(this.offset + offset, alignment);
    }
}
