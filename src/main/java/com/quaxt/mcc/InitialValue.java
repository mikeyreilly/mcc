package com.quaxt.mcc;

public sealed interface InitialValue permits InitialValue.Tentative, InitialConstant, InitialValue.NoInitializer {
    enum Tentative implements InitialValue {TENTATIVE}
    enum NoInitializer implements InitialValue {NO_INITIALIZER}
}
