package com.quaxt.mcc.parser.parser2;


import com.quaxt.mcc.Err;
import com.quaxt.mcc.Token;
import com.quaxt.mcc.TokenWithValue;
import com.quaxt.mcc.parser.MemberDeclaration;
import com.quaxt.mcc.parser.Parser;
import com.quaxt.mcc.parser.StorageClass;
import com.quaxt.mcc.semantic.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.quaxt.mcc.TokenType.*;

public class Parser2 {

    public static List<DeclarationSpecifier> parseDeclarationSpecifiers(
            ArrayList<Token> tokens, ArrayList<Map<String, Type>> typeAliases) {
        // declaration-specifiers:
        //        storage-class-specifier declaration-specifiers(opt)
        //        type-specifier declaration-specifiersopt
        //        type-qualifier declaration-specifiers(opt) (none of these
        //        are supported yet)
        //        function-specifier declaration-specifiers(opt) (not supported)
        StorageClass storageClass;
        TypeSpecifier typeSpecifier;

        var l = new ArrayList<DeclarationSpecifier>();
        while (true) {
            if (tokens.isEmpty() || tokens.getFirst().equals(SEMICOLON)) break;
            if ((storageClass = parseStorageClassSpecifier(tokens)) != null) {
                l.add(storageClass);
            } else if ((typeSpecifier = parseTypeSpecifier(tokens,
                    typeAliases)) != null) {
                typeAliases = null; // we only want to recognize a typedef
                                    // name as a typedef name if it is the first typeSpecifier
                l.add(typeSpecifier);
            } else break;
        }
        return l;


    }



    private static TypeSpecifier parseTypeSpecifier(ArrayList<Token> tokens,
                                                    ArrayList<Map<String,
                                                            Type>> typeAliases) {
        TypeSpecifier ts;
        switch (tokens.getFirst()) {
            case VOID -> ts = PrimitiveTypeSpecifier.VOID;
            case CHAR -> ts = PrimitiveTypeSpecifier.CHAR;
            case INT -> ts = PrimitiveTypeSpecifier.INT;
            case LONG -> ts = PrimitiveTypeSpecifier.LONG;
            case DOUBLE -> ts = PrimitiveTypeSpecifier.DOUBLE;
            case SIGNED -> ts = PrimitiveTypeSpecifier.SIGNED;
            case UNSIGNED -> ts = PrimitiveTypeSpecifier.UNSIGNED;
            default -> ts = null;
        }
        if (ts != null) tokens.removeFirst();
        else {
            ts = parseStructOrUnionSpecifier(tokens, typeAliases);
            if (ts == null && typeAliases != null) ts = parseTypedefName(tokens, typeAliases);
        }
        return ts;
    }

    private static TypeSpecifier parseTypedefName(ArrayList<Token> tokens,
                                                  ArrayList<Map<String, Type>> typeAliases) {
        var first = tokens.getFirst();
        if (first instanceof TokenWithValue(Token type,
                                            String value) && type == IDENTIFIER) {

            if (Parser.findTypeByName(typeAliases, value) != null) {
                tokens.removeFirst();
                return new TypedefName(value);
            }
        }
        return null;

    }

    private static StructOrUnionSpecifier parseStructOrUnionSpecifier(
            ArrayList<Token> tokens, ArrayList<Map<String, Type>> typeAliases) {
        boolean isUnion=false;
        switch (tokens.getFirst()) {
            case STRUCT -> {
                isUnion = false;
            }
            case UNION -> {
                isUnion = true;
            }
            default -> {
                return null;
            }
        }

        String tag = null;
        ArrayList<MemberDeclaration> members = null;

        tokens.removeFirst(); //struct-or-union
        Token first = tokens.getFirst();

        if (first instanceof TokenWithValue(Token type,
                                            String value) && type == IDENTIFIER) {
            tokens.removeFirst();
            first = tokens.getFirst();
            tag = value;
        }
        if (first.equals(OPEN_BRACE)) {
            tokens.removeFirst();
            members = new ArrayList<>();
            while (tokens.getFirst() != CLOSE_BRACE) {
                members.add(Parser.parseMemberDeclaration(tokens, typeAliases));
                Parser.expect(SEMICOLON, tokens);
            }
            tokens.removeFirst(); // closing brace
        } else if (tag == null) {
            throw new Err("Expected either union identifer or '{', found: "+tokens.removeFirst());
        }
        return new StructOrUnionSpecifier(isUnion, tag, members);
    }

    private static StorageClass parseStorageClassSpecifier(
            ArrayList<Token> tokens) {
        StorageClass sc;
        switch (tokens.getFirst()) {
            case TYPEDEF -> sc = StorageClass.TYPEDEF;
            case EXTERN -> sc = StorageClass.EXTERN;
            case STATIC -> sc = StorageClass.STATIC;
            default -> {
                return null;
            }
        }
        tokens.removeFirst();
        return sc;

    }
}
