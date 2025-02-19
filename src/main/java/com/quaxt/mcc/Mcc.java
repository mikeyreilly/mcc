package com.quaxt.mcc;

import com.quaxt.mcc.asm.Codegen;
import com.quaxt.mcc.asm.ProgramAsm;
import com.quaxt.mcc.parser.Parser;
import com.quaxt.mcc.parser.Program;
import com.quaxt.mcc.semantic.SemanticAnalysis;
import com.quaxt.mcc.tacky.IrGen;
import com.quaxt.mcc.tacky.ProgramIr;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class Mcc {

    public static final HashMap<String, SymbolTableEntry> SYMBOL_TABLE = new HashMap<>(){
        @Override
        public SymbolTableEntry put(String key, SymbolTableEntry value) {
            return super.put(key, value);
        }
    };

    public static final AtomicLong TEMP_COUNT = new AtomicLong(0L);

    public static String makeTemporary(String prefix) {
        return prefix + TEMP_COUNT.getAndIncrement();
    }

    enum Mode {LEX, PARSE, VALIDATE, CODEGEN, COMPILE, TACKY, ASSEMBLE}

    public static int preprocess(Path cFile, Path iFile) throws IOException, InterruptedException {
        ProcessBuilder pb =
                new ProcessBuilder("gcc", "-E", "-P", cFile.toString(), "-o", iFile.toString()).inheritIO();
        return pb.start().waitFor();
    }

    private static int assembleAndLink(Path asmFile, String bareFileName, boolean doNotCompile, List<String> libs) throws InterruptedException, IOException {
        List<String> gccArgs = new ArrayList<>(Arrays.asList("gcc", asmFile.toString()));
        if (doNotCompile) {
            gccArgs.add("-c");
        }
        gccArgs.addAll(Arrays.asList("-o", asmFile.resolveSibling(bareFileName + (doNotCompile ? ".o" : "")).toString()));
        gccArgs.addAll(libs);
        ProcessBuilder pb =
                new ProcessBuilder(gccArgs.toArray(new String[0])).inheritIO();
        return pb.start().waitFor();
    }

    public static void main(String[] args0) throws Exception {
        ArrayList<String> args = Arrays.stream(args0)
                .collect(Collectors.toCollection(ArrayList::new));
        Mode mode = Mode.ASSEMBLE;
        boolean doNotCompile = false;
        List<String> libs = new ArrayList<>();
        for (int i = args.size() - 1; i >= 0; i--) {
            String arg = args.get(i);
            Mode newMode = switch (arg) {
                case "--lex" -> Mode.LEX;
                case "--parse" -> Mode.PARSE;
                case "--validate" -> Mode.VALIDATE;
                case "--codegen" -> Mode.CODEGEN;
                case "--tacky" -> Mode.TACKY;
                case "-S" -> Mode.COMPILE;
                case "-c" -> {
                    doNotCompile = true;
                    args.remove(i);
                    yield null;
                }
                default -> {
                    if (arg.startsWith("-l")) {
                        libs.addFirst(arg);
                        args.remove(i);
                    }
                    yield null;
                }
            };
            if (newMode != null) {
                mode = newMode;
                args.remove(i);
            }
        }
        Path srcFile = Path.of(args.getFirst());
        if (args.size() > 1) {
            System.err.println("unrecognized argument: " + args.get(1));
            System.exit(-1);
        }
        String bareFileName = removeEnding(srcFile.getFileName().toString());
        Path intermediateFile = srcFile.resolveSibling(bareFileName + ".i");

        int preprocessExitCode = preprocess(srcFile, intermediateFile);
        if (preprocessExitCode != 0) {
            System.exit(preprocessExitCode);
        }
        List<Token> l = Lexer.lex(Files.readString(intermediateFile));
        Files.delete(intermediateFile);
        if (mode == Mode.LEX) {
            return;
        }
        Program program = Parser.parseProgram(l);
        if (!l.isEmpty()) {
            throw new IllegalArgumentException("Unexpected token " + l.getFirst());
        }
        if (mode == Mode.PARSE) {
            return;
        }

        program = SemanticAnalysis.resolveProgram(program);
        SemanticAnalysis.typeCheckProgram(program);
        program = SemanticAnalysis.loopLabelProgram(program);
        if (mode == Mode.VALIDATE) {
            return;
        }
        ProgramIr programIr = IrGen.programIr(program);
        if (mode == Mode.TACKY) {
            return;
        }
        ProgramAsm programAsm = Codegen.generateProgramAssembly(programIr);
        if (mode == Mode.CODEGEN) {
            return;
        }
        Path asmFile = srcFile.resolveSibling(bareFileName + ".s");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(asmFile))) {
            programAsm.emitAsm(pw);
            pw.flush();
        }

        if (mode == Mode.COMPILE) {
            return;
        }
        int exitCode = assembleAndLink(asmFile, bareFileName, doNotCompile, libs);
//        if (exitCode == 0) {
        Files.delete(asmFile);
//        }
        System.exit(exitCode);
    }


    private static String removeEnding(String fileName) {
        String ending = ".c";
        if (fileName.endsWith(ending)) {
            return fileName.substring(0, fileName.length() - ending.length());
        }
        throw new IllegalArgumentException(fileName + " does not have ending " + ending);
    }
}
