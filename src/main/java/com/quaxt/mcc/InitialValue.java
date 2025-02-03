package com.quaxt.mcc;

public sealed interface InitialValue permits InitialValue.NoInitializer, InitialValue.Tentative, IntInit, LongInit {
    enum Tentative implements InitialValue {TENTATIVE}
    enum NoInitializer implements InitialValue {NO_INITIALIZER}
}
