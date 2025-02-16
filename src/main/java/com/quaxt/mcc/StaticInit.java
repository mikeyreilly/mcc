package com.quaxt.mcc;

public sealed interface StaticInit extends InitialValue permits DoubleInit, IntInit, LongInit, UIntInit, ULongInit {
}
