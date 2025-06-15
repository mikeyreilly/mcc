package com.quaxt.mcc;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MccTest {

    @Test
    void canReturn42() throws Exception {
        returns("hello", 42);
    }


    @Test
    void typedef() throws Exception {
        returns("typedef", 0);
    }

    @Test
    void struct() throws Exception {
        returns("struct", 0);
    }

    @Test
    void cast() throws Exception {
        returns("cast", 0);
    }

    @Test
    void multipleInitDeclarators() throws Exception {
        returns("multiple_init_declarators",0);
    }
    private static void returns(String testProgram,
                                int expectedExitCode) throws Exception {
        assertEquals(0, Mcc.mcc("src/test/resources/" + testProgram + ".c"));
        assertEquals(expectedExitCode,
                Mcc.startProcess("src/test/resources/" + testProgram));
    }

    @AfterAll
    static void tearDown() {
        try (var st = Files.walk(Paths.get("src/test/resources"))) {
            st.forEach((Path f) -> {
                if (Files.isRegularFile(f)) {
                    String name = f.toString();
                    if (!name.endsWith(".c") && !name.endsWith(".h")) {
                        try {
                            Files.delete(f);
                        } catch (IOException e) {
                            System.err.println("exception deleting: " + f);
                            e.printStackTrace();
                        }
                    }
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
