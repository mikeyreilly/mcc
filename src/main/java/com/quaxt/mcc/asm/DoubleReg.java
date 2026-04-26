package com.quaxt.mcc.asm;

import java.util.Arrays;

public enum DoubleReg implements HardReg {
    XMM0("xmm0", 17),
    XMM1("xmm1", 18),
    XMM2("xmm2", 19),
    XMM3("xmm3", 20),
    XMM4("xmm4", 21),
    XMM5("xmm5", 22),
    XMM6("xmm6", 23),
    XMM7("xmm7", 24),
    XMM8("xmm8", 25),
    XMM9("xmm9", 26),
    XMM10("xmm10", 27),
    XMM11("xmm11", 28),
    XMM12("xmm12", 29),
    XMM13("xmm13", 30),
    XMM14("xmm14", 31),
    XMM15("xmm15", 32);

    private final String name;
    public final int dwarfNumber;

    DoubleReg(String name, int dwarfNumber){
        this.name = name;
        this.dwarfNumber = dwarfNumber;
    }

    public String toString(){
        return name;
    }
}
