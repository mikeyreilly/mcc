package com.quaxt.mcc.asm;

import java.util.List;
import java.util.Objects;

public final class ReturnValueClassification {
    public final List<TypedOperand> intDests;
    public final List<Operand> doubleDests;
    public final boolean returnInMemory;

    public ReturnValueClassification(List<TypedOperand> intDests, List<Operand> doubleDests,
                                     boolean returnInMemory) {
        this.intDests = intDests;
        this.doubleDests = doubleDests;
        this.returnInMemory = returnInMemory;
    }

    public List<TypedOperand> intDests() {return intDests;}

    public List<Operand> doubleDests() {return doubleDests;}

    public boolean returnInMemory() {return returnInMemory;}

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (ReturnValueClassification) obj;
        return Objects.equals(this.intDests, that.intDests) && Objects.equals(this.doubleDests, that.doubleDests) && this.returnInMemory == that.returnInMemory;
    }

    @Override
    public int hashCode() {
        return Objects.hash(intDests, doubleDests, returnInMemory);
    }

    @Override
    public String toString() {
        return "ReturnValueClassification[" + "intDests=" + intDests + ", " + "doubleDests=" + doubleDests + ", " + "returnInMemory=" + returnInMemory + ']';
    }
}
