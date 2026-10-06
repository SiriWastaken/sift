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
        assertTrue(o.matches("(?s).*\\nproject\\s+" + java.util.regex.Pattern.quote(dir.getFileName().toString()) + "\\n.*"), o);
        assertTrue(o.matches("(?s).*\\nlanguage\\s+Java\\n.*"), o);
        assertTrue(o.matches("(?s).*\\nbuild\\s+Maven\\n.*"), o);
    }

    @Test void inspectSaysUnknownRatherThanGuessing() {
        run("inspect");
        assertTrue(out().matches("(?s).*\\nlanguage\\s+unknown\\n.*"), out());
        assertTrue(out().matches("(?s).*\\nframework\\s+unknown\\n.*"), out());
        assertTrue(out().matches("(?s).*\\nbuild\\s+unknown\\n.*"), out());
        assertTrue(out().matches("(?s).*\\ntests\\s+unknown\\n.*"), out());
    }

    @Test void inspectReportsStackSizeAndSecondaryLanguages() throws IOException {
        Files.writeString(dir.resolve("package.json"),
                "{\"dependencies\": {\"next\": \"14\", \"react\": \"18\"}, \"devDependencies\": {\"vitest\": \"1\"}}");
        Files.writeString(dir.resolve("pnpm-lock.yaml"), "");
        Files.writeString(dir.resolve("tsconfig.json"), "{}");
        Files.writeString(dir.resolve("a.ts"), "export {}");
        Files.writeString(dir.resolve("tool.py"), "pass");
        assertEquals(Main.OK, run("inspect"));
        String o = out();
        assertTrue(o.matches("(?s).*\\nlanguage\\s+TypeScript\\n.*"), o);
        assertTrue(o.matches("(?s).*\\nalso\\s+Python\\n.*"), o);
        assertTrue(o.matches("(?s).*\\nframework\\s+Next\\.js\\n.*"), o);
        assertTrue(o.matches("(?s).*\\npackage\\s+pnpm\\n.*"), o);
        assertTrue(o.matches("(?s).*\\ntests\\s+vitest\\n.*"), o);
        assertTrue(o.matches("(?s).*\\nfiles\\s+5 \\(.*\\)\\n.*"), o);
    }
}
