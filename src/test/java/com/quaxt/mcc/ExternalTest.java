package com.quaxt.mcc;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.quaxt.mcc.MccTest.matchesGcc;
import static com.quaxt.mcc.MccTest.outputs;

// The test sources live outside this repo because I'm not sure if they are GPL
public class ExternalTest {
    @Test
    void gccTorture() throws Exception {
        Path tortureSrcFiles = Paths.get(System.getProperty("user.home"),"opt/gcc/gcc/testsuite/gcc.c-torture/execute");
        if (Files.exists(tortureSrcFiles)) {
            ArrayList<Path> l =
                    (ArrayList<Path>) Files.list(tortureSrcFiles).sorted((o1, o2) -> {
                        try {
                            return Long.compare(Files.size(o1), Files.size(o2));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }).collect(Collectors.toList());
            for (int i =0;i<l.size();i++) {
                Path p=l.get(i);
                if (p.toString().endsWith(".c")) {
                    System.out.println(i+"/"+l.size()+":"+p);
                    matchesGcc(p, false, false);
                }
            }
        }
    }
}