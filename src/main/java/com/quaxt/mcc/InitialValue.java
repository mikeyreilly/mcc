package com.quaxt.mcc;

public sealed interface InitialValue permits InitialValue.NoInitializer, InitialValue.Tentative, StaticInit {
    enum Tentative implements InitialValue {TENTATIVE}
    enum NoInitializer implements InitialValue {NO_INITIALIZER}
}
