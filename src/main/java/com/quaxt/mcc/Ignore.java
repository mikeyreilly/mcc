package com.quaxt.mcc;

import com.quaxt.mcc.tacky.InstructionIr;

/**
 * When optimizing we sometimes get rid of instructions - replacing them with Ignore is more efficient then actually removing them from the ArrayList
 * */
public record Ignore() implements InstructionIr {}
