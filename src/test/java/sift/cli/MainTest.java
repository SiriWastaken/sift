// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Sri Ganty

package sift.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import sift.Main;

class MainTest {

    @TempDir Path dir;

    private final ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errBuf = new ByteArrayOutputStream();

    private int run(String... args) {
        return Main.run(args, dir,
                new PrintStream(outBuf, true, StandardCharsets.UTF_8),
                new PrintStream(errBuf, true, StandardCharsets.UTF_8));
    }

    private String out() { return outBuf.toString(StandardCharsets.UTF_8); }
    private String err() { return errBuf.toString(StandardCharsets.UTF_8); }

    @Test void helpSucceeds() {
        assertEquals(Main.OK, run("--help"));
        assertTrue(out().contains("inspect"));
    }

    @Test void noArgumentsShowsHelp() {
        assertEquals(Main.OK, run());
        assertTrue(out().contains("Usage:"));
    }

    @Test void versionSucceeds() {
        assertEquals(Main.OK, run("--version"));
        assertTrue(out().startsWith("sift "));
    }

    @Test void unknownCommandIsUsageError() {
        assertEquals(Main.USAGE, run("banana"));
        assertTrue(err().contains("Unknown command: banana"));
        assertTrue(err().contains("sift --help"));
        assertEquals("", out());
    }

    @Test void inspectDescribesProject() throws IOException {
        Files.createFile(dir.resolve("pom.xml"));
        assertEquals(Main.OK, run("inspect"));
        String o = out();
        assertTrue(o.contains("project   " + dir.getFileName()), o);
        assertTrue(o.contains("language  Java"), o);
        assertTrue(o.contains("build     Maven"), o);
    }

    @Test void inspectSaysUnknownRatherThanGuessing() {
        run("inspect");
        assertTrue(out().contains("language  unknown"));
        assertTrue(out().contains("build     unknown"));
    }
}
