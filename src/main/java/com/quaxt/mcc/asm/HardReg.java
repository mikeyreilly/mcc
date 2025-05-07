package com.quaxt.mcc.asm;

import java.util.Arrays;

public enum HardReg implements Reg {
    // callee saved %r12, %r13, %r14, %r15, %rbx, %rsp, %rbp
    AX("rax","eax","ax","al", false),
    CX("rcx","ecx","cx","cl", false),
    DX("rdx","edx","dx","dl", false),
    BX("rbx","ebx","bx","bl", true),
    SI("rsi","esi","si","sil", false),
    DI("rdi","edi","di","dil", false),
    SP("rsp","esp","sp","spl", true),
    BP("rbp","ebp","bp","bpl", true),
    R8("r8","r8d","r8w","r8b", false),
    R9("r9","r9d","r9w","r9b", false),
    R10("r10","r10d","r10w","r10b", false),
    R11("r11","r11d","r11w","r11b", false),
    R12("r12","r12d","r12w","r12b",true),
    R13("r13","r13d","r13w","r13b", true),
    R14("r14","r14d","r14w","r14b", true),
    R15("r15","r15d","r15w","r15b", true);

    public final String b; // 1 byte
    public final String w; // word 2 bytes
    public final String d; // dword 4 bytes
    public final String q; //qword 8 bytes
    public final boolean isCalleeSaved;

    HardReg(String q, String d, String w, String b, boolean isCalleeSaved) {
        this.q=q;
        this.d=d;
        this.w=w;
        this.b=b;
        this.isCalleeSaved = isCalleeSaved;
    }
}
