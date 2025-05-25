package com.quaxt.mcc.asm;

import java.util.Arrays;

public enum DoubleReg implements HardReg {
    XMM0("xmm0"),
    XMM1("xmm1"),
    XMM2("xmm2"),
    XMM3("xmm3"),
    XMM4("xmm4"),
    XMM5("xmm5"),
    XMM6("xmm6"),
    XMM7("xmm7"),
    XMM8("xmm8"),
    XMM9("xmm9"),
    XMM10("xmm10"),
    XMM11("xmm11"),
    XMM12("xmm12"),
    XMM13("xmm13"),
    XMM14("xmm14"),
    XMM15("xmm15");

    private final String name;
    DoubleReg(String name){
        this.name = name;
    }

    public String toString(){
        return name;
    }
}
