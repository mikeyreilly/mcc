package com.quaxt.mcc.parser;

import com.quaxt.mcc.Token;

import java.util.ArrayList;
import java.util.stream.Stream;

public class TokenList  {
    String currentFilename;
    int currentLineNumber;
    public ArrayList<String> fileNames = new ArrayList<>();
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

    public void discardFirst() {
        cursor++;
    }

    public Token removeFirst() {
        var r= getFirst();
        cursor++;
        return r;
    }

    int positionsIndex=0;


    /**
     * @return the highest index into positions that is on or before cursor
     */
    public int getCurrentPosition() {
        for(; positionsIndex < positions.size()-1; positionsIndex++) {
            int index = positions.get(positionsIndex).tokenIndex();
            if (index == cursor) break;
            if (index > cursor) {
                positionsIndex--;
                break;
            }
        }
        return positionsIndex;
    }


    public TokenList back() {
        cursor--;
        for(; positionsIndex > 0; positionsIndex--) {
            int index = positions.get(positionsIndex).tokenIndex();
            if (index <= cursor) break;
        }
        return this;
    }

    public Token get(int i) {
        return tokens.get(i + cursor);
    }

    public String positionString() {
        int positionsIndex = 0;
        for(positionsIndex = 1;positionsIndex<positions.size();positionsIndex++) {
            if (positions.get(positionsIndex).tokenIndex() == cursor) break;
            if (positions.get(positionsIndex).tokenIndex() > cursor) {
                positionsIndex--;
                break;
            }
        }
        if (positionsIndex >= positions.size()) {
            positionsIndex = positions.size() - 1;
        }
        var pos = positions.get(positionsIndex);

        return " at " + fileNames.get(pos.file()) + ":" + (pos.lineNumber() + 1);
    }

    public Stream<Token> stream() {
        return tokens.subList(cursor, tokens.size()).stream();
    }

    public void add(Token token, String filename, int lineNumber) {
        int currentIndex = tokens.size();
        if (!filename.equals(currentFilename)) {
            currentFilename = filename;
            file = fileNames.indexOf(currentFilename);
            if (file == -1) {
                file = fileNames.size();
                fileNames.add(currentFilename);
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
