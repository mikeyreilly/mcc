package com.quaxt.mcc.asm;

public enum Reg implements Operand{
    AX("ax","eax"),
    DX("dx","edx"),
    R11("r11b","r11d"),
    R10("r10b","r10d");

    public final String b;
    public final String d;

    Reg(String b, String d) {
        this.b=b;
        this.d=d;
    }

    public static void main(String[] args) {
        for (Reg reg: Reg.values()){
            System.out.print(reg+"(");
            for (char c:"bd".toCharArray()){
              String s =  switch (c) {
                    case 'b' -> reg.toString()+"b,";
                    default -> reg.toString()+"d";
                };
                System.out.print(s);
            }
            System.out.println(");");
        }


    }


}
