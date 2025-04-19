package com.quaxt.mcc.parser;

import com.quaxt.mcc.semantic.Type;

import java.util.ArrayList;

public record CompoundInit(ArrayList<Initializer> inits, Type type) implements Initializer {}
