package com.quaxt.mcc;

import com.quaxt.mcc.parser.Constant;
import com.quaxt.mcc.parser.Exp;
import com.quaxt.mcc.parser.UnaryOp;
import com.quaxt.mcc.semantic.Type;
import com.quaxt.mcc.tacky.ValIr;

import static com.quaxt.mcc.ArithmeticOperator.*;
import static com.quaxt.mcc.CmpOperator.*;
import static com.quaxt.mcc.UnaryOperator.POST_INCREMENT;
import static com.quaxt.mcc.semantic.Primitive.CHAR;

public record ConstantExp(Exp exp) implements  Constant<ConstantExp> {

    @Override
    public long toLong() {
        throw new IllegalStateException();
    }

    @Override
    public Constant<?> apply(BinaryOperator op, ConstantExp v2) {
        throw new IllegalStateException();
    }

    @Override
    public boolean isZero() {
        throw new IllegalStateException();
    }

    @Override
    public Constant apply(UnaryOperator op) {
        return new ConstantExp(new UnaryOp(op, this.exp(), this.type()));
    }

    @Override
    public Constant<?> apply1(BinaryOperator op, Constant c2) {
        throw new IllegalStateException();
    }

    @Override
    public Type type() {
        return exp.type();
    }
}