// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Sri Ganty

package sift.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import sift.exec.Exec.Outcome;

class ExecTest {

    @TempDir Path dir;

    // The JVM running the tests is the one program guaranteed to exist everywhere.
    private static final String JAVA = Path.of(System.getProperty("java.home"), "bin", "java").toString();

    @Test void successCapturesOutput() {
        Exec.Result r = Exec.combined(dir, Duration.ofSeconds(30), List.of(JAVA, "-version"));
        assertTrue(r.ok());
        assertTrue(r.output().contains("version"));
    }

    @Test void nonZeroExitIsReported() {
        Exec.Result r = Exec.combined(dir, Duration.ofSeconds(30), List.of(JAVA, "--no-such-option"));
        assertEquals(Outcome.EXITED, r.outcome());
        assertFalse(r.ok());
        assertTrue(r.exit() != 0);
    }

    @Test void stdoutOnlyDropsStderr() {
        // `java -version` writes to stderr only.
        Exec.Result r = Exec.stdout(dir, Duration.ofSeconds(30), List.of(JAVA, "-version"));
        assertTrue(r.ok());
        assertEquals("", r.output());
    }

    @Test void missingProgramIsNotFound() {
        Exec.Result r = Exec.combined(dir, Duration.ofSeconds(5), List.of("sift-no-such-program"));
        assertEquals(Outcome.NOT_FOUND, r.outcome());
    }

    @Test void silentProcessIsKilledAtTimeout() throws Exception {
        Files.writeString(dir.resolve("Sleep.java"),
                "public class Sleep { public static void main(String[] a) throws Exception { Thread.sleep(60_000); } }");
        long start = System.nanoTime();
        Exec.Result r = Exec.combined(dir, Duration.ofSeconds(2), List.of(JAVA, "Sleep.java"));
        assertEquals(Outcome.TIMED_OUT, r.outcome());
        assertTrue(Duration.ofNanos(System.nanoTime() - start).toSeconds() < 30);
    }
}
