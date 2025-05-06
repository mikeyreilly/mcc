package com.quaxt.mcc;

import com.quaxt.mcc.asm.DoubleReg;
import com.quaxt.mcc.asm.HardReg;

public record Pair<K, V>(K key, V value) {
}
