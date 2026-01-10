package com.quaxt.mcc;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static com.quaxt.mcc.MccTest.matchesGcc;
// The test sources live outside this repo because I'm not sure if they are GPL
public class ExternalTest {
    @Test
    void gccTorture() throws Exception {
        Path tortureSrcFiles = Paths.get(System.getProperty("user.home"),"opt/gcc/gcc/testsuite/gcc.c-torture/execute");
        if (Files.exists(tortureSrcFiles)) {
            List<Path> l = Files.list(tortureSrcFiles).sorted((o1, o2) -> {
                try {
                    return Long.compare(Files.size(o1), Files.size(o2));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).toList();
            for (Path p:l) {
                if (p.toString().endsWith(".c")) {
                    System.out.println(p);
                    matchesGcc(p, false, false);
                }
            }
        }
    }
}
