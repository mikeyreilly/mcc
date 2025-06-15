package com.quaxt.mcc.parser.parser2;

import com.quaxt.mcc.parser.StorageClass;

public sealed interface DeclarationSpecifier permits StorageClass, TypeSpecifier {}
