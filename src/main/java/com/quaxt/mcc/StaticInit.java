package com.quaxt.mcc;

import com.quaxt.mcc.tacky.CharInit;
import com.quaxt.mcc.tacky.UCharInit;

public sealed interface StaticInit permits DoubleInit, IntInit, LongInit, UIntInit, ULongInit, ZeroInit, CharInit, UCharInit {
}
