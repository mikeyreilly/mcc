package com.quaxt.mcc.asm;

import com.quaxt.mcc.CmpOperator;

public record OldJmpCC(CmpOperator cmpOperator, boolean unsigned,
                       String label)  {
    JmpCC toJmpCC2() {

        CC cc = switch (cmpOperator) {
            case EQUALS -> CC.E;
            case NOT_EQUALS -> CC.NE;
            case LESS_THAN_OR_EQUAL -> !unsigned ? CC.LE : CC.BE;
            case GREATER_THAN_OR_EQUAL -> !unsigned ? CC.GE : CC.AE;
            case LESS_THAN -> !unsigned ? CC.L : CC.B;
            case GREATER_THAN -> !unsigned ? CC.G : CC.A;
            case null -> unsigned ? CC.P:CC.NP;
        };
        return new JmpCC(cc, label);
    }
}
