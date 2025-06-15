package com.quaxt.mcc.parser;

import java.util.List;

public record DeclarationList(List<Declaration> list) implements ForInit {}
