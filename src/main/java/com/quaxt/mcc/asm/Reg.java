package com.quaxt.mcc.asm;

import java.util.Arrays;

public enum Reg implements Operand {
    AX("rax","eax","ax","al"),
    CX("rcx","ecx","cx","cl"),
    DX("rdx","edx","dx","dl"),
    BX("rbx","ebx","bx","bl"),
    SI("rsi","esi","si","sil"),
    DI("rdi","edi","di","dil"),
    SP("rsp","esp","sp","spl"),
    BP("rbp","ebp","bp","bpl"),
    R8("r8","r8d","r8w","r8b"),
    R9("r9","r9d","r9w","r9b"),
    R10("r10","r10d","r10w","r10b"),
    R11("r11","r11d","r11w","r11b"),
    R12("r12","r12d","r12w","r12b"),
    R13("r13","r13d","r13w","r13b"),
    R14("r14","r14d","r14w","r14b"),
    R15("r15","r15d","r15w","r15b");

    public final String b; // 1 byte
    public final String w; // word 2 bytes
    public final String d; // dword 4 bytes
    public final String q; //qword 8 bytes

    Reg(String q, String d, String w, String b) {
        this.q=q;
        this.d=d;
        this.w=w;
        this.b=b;
    }

    public static void main(String[] args) {
        System.out.println(Arrays.toString(Reg.values()));
    }
}
