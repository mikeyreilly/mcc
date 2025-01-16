package com.quaxt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Parser {

    private static List<Token> expect(Token expected, List<Token> tokens) {
        Token token = tokens.removeFirst();
        if (expected != token) {
            throw new IllegalArgumentException("Expected " + expected + ", got " + token);
        }
        return tokens;
    }

    static Node parseStatement(List<Token> tokens) {
        expect(Token.RETURN, tokens);
        Expr expr = parseExpr(tokens);
        expect(Token.SEMICOLON, tokens);
        return new Return(expr);
    }

    public static Program parseProgram(List<Token> tokens) {
        Function function = parseFunction(tokens);
        return new Program(function);
    }

    private static Token parseType(List<Token> tokens) {
        Token type = tokens.removeFirst();
        if (!type.isType()){
            throw new IllegalArgumentException("Expected type, got " + type);
        }
        return type;
    }

    private static String parseIdentifier(List<Token> tokens) {
        Token identifier = tokens.removeFirst();
        if (identifier.type()!=TokenType.IDENTIFIER){
            throw new IllegalArgumentException("Expected identifier, got " + identifier);
        }
        return identifier.value();
    }
    private static Function parseFunction(List<Token> tokens) {
        Token returnType = parseType(tokens);
        String name = parseIdentifier(tokens);
        expect(Token.OPEN_PAREN, tokens);
        expect(Token.VOID, tokens);
        expect(Token.CLOSE_PAREN, tokens);
        expect(Token.OPEN_BRACE, tokens);
        Node statement = parseStatement(tokens);
        expect(Token.CLOSE_BRACE, tokens);

        return new Function(name, returnType, statement);
    }


    private static Expr parseExpr(List<Token> tokens) {
        Token token = tokens.removeFirst();
        if (TokenType.NUMERIC != token.type()){
            throw new IllegalArgumentException("Expected int, got " + token);
        }
        return new Int(Integer.parseInt(token.value()));
    }


    static Node parse(List<Token> tokens) {
        return null;
    }

    public static void main(String[] args) throws IOException {
        System.out.println(parseProgram(Lexer.lex(Files.readString(Paths.get("/home/mreilly/wa/writing-a-c-compiler-tests/tests/chapter_1/invalid_parse/end_before_expr.c")))));
    }

}
