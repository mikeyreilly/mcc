package com.quaxt.mcc.parser;

import com.quaxt.mcc.Token;
import com.quaxt.mcc.TokenType;

import java.util.ArrayList;

public class TokenList  {
    String currentFilename;
    int currentLineNumber;
    ArrayList<String> filenames = new ArrayList<>();
    ArrayList<Position> positions = new ArrayList<>();
    private ArrayList<Token> tokens = new ArrayList<Token>();
    int file = -1;
    int cursor = 0;
    public boolean isEmpty() {
        return tokens.size() - cursor <= 0;
    }

    public Token getFirst() {
        return tokens.get(cursor);
    }

    public Token removeFirst() {
        var r= getFirst();
        cursor++;
        return r;
    }

    public Token get(int i) {
        return tokens.get(i + cursor);
    }

    public String positionString() {
        int positionsIndex = 0;
        for(positionsIndex = 1;positionsIndex<positions.size();positionsIndex++) {
            if (positions.get(positionsIndex).tokenIndex==cursor) break;
            if (positions.get(positionsIndex).tokenIndex>cursor) {
                positionsIndex--;
                break;
            }
        }

        var pos = positions.get(positionsIndex);

        return " at " + filenames.get(pos.file) + ":" + (pos.lineNumber + 1);
    }

    record Position(int file, int lineNumber, int tokenIndex) {}

    public void add(Token token, String filename, int lineNumber) {
        int currentIndex = tokens.size();
        if (!filename.equals(currentFilename)) {
            currentFilename = filename;
            file = filenames.indexOf(currentFilename);
            if (file == -1) {
                file = filenames.size();
                filenames.add(currentFilename);
            }
            positions.add(new Position(file, lineNumber, currentIndex));
            currentLineNumber = lineNumber;
        } else if (lineNumber != currentLineNumber) {
            positions.add(new Position(file, lineNumber, currentIndex));
            currentLineNumber = lineNumber;
        }
        tokens.add(token);
    }

}
