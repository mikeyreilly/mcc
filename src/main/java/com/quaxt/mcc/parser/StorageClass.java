package com.quaxt.mcc.parser;

import com.quaxt.mcc.parser.parser2.DeclarationSpecifier;

public enum StorageClass implements DeclarationSpecifier {
    STATIC, EXTERN,
    /**
     * <blockquote>
     * The typedef specifier is called a ‘‘storage-class specifier’’ for
     * syntactic convenience only<br>
     * ISO/IEC 9899:1999 6.7.1
     * </blockquote>
     */
    TYPEDEF
}
