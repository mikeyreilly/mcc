package com.quaxt.mcc.parser;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;

public record Program(ArrayList<Declaration> declarations,
                      ArrayList<Position> positions) {
    public Iterable<Function> functions() {
        return new FunctionIterable();
    }

    private  class FunctionIterable implements Iterable<Function> {

        @Override
        public Iterator<Function> iterator() {
            return new FunctionIterator();
        }

        private  class FunctionIterator implements Iterator<Function> {

            private int cursor;
            FunctionIterator(){
                cursor = 0;
                skipNonFunctions();
            }
            void skipNonFunctions(){
                while (cursor < declarations.size() && !(declarations.get(cursor) instanceof Function)){
                    cursor++;
                }
            }

            @Override
            public boolean hasNext() {
                skipNonFunctions();
                return cursor < declarations.size();
            }

            @Override
            public Function next() {
                int i = cursor;
                if (i >= declarations.size()) {
                    throw new NoSuchElementException();
                }
                cursor++;
                skipNonFunctions();
                return (Function) declarations.get(i);
            }
        }
    }
}

