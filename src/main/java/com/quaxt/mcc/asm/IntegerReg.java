package com.quaxt.mcc.asm;

public enum IntegerReg implements HardReg {
    // callee saved %r12, %r13, %r14, %r15, %rbx, %rsp, %rbp
    AX("rax","eax","ax","al", false, 0),
    CX("rcx","ecx","cx","cl", false, 2),
    DX("rdx","edx","dx","dl", false, 1),
    BX("rbx","ebx","bx","bl", true, 3),
    SI("rsi","esi","si","sil", false, 4),
    DI("rdi","edi","di","dil", false, 5),
    SP("rsp","esp","sp","spl", true, 7),
    BP("rbp","ebp","bp","bpl", true, 6),
    R8("r8","r8d","r8w","r8b", false, 8),
    R9("r9","r9d","r9w","r9b", false, 9),
    R10("r10","r10d","r10w","r10b", false, 10),
    R11("r11","r11d","r11w","r11b", false, 11),
    R12("r12","r12d","r12w","r12b",true, 12),
    R13("r13","r13d","r13w","r13b", true, 13),
    R14("r14","r14d","r14w","r14b", true, 14),
    R15("r15","r15d","r15w","r15b", true, 15);

    public final String b; // 1 byte
    public final String w; // word 2 bytes
    public final String d; // dword 4 bytes
    public final String q; //qword 8 bytes
    public final boolean isCalleeSaved;
    public final int dwarfNumber;

    IntegerReg(String q, String d, String w, String b, boolean isCalleeSaved, int dwarfNumber) {
        this.q=q;
        this.d=d;
        this.w=w;
        this.b=b;
        this.isCalleeSaved = isCalleeSaved;
        this.dwarfNumber = dwarfNumber;
    }
}
