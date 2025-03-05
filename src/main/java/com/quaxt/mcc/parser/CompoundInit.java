package com.quaxt.mcc.parser;

import java.util.ArrayList;

public record CompoundInit(ArrayList<Initializer> inits) implements Initializer {}
