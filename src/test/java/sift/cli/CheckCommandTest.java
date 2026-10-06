// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Sri Ganty

package sift.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static sift.TestRepo.write;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import sift.Main;

class CheckCommandTest {

    @TempDir Path dir;

    private final ByteArrayOutputStream outBuf = new ByteArrayOutputStream();

    private int check() {
        return Main.run(new String[] {"check"}, dir,
                new PrintStream(outBuf, true, StandardCharsets.UTF_8), System.err);
    }

    private String out() { return outBuf.toString(StandardCharsets.UTF_8); }

    /**
     * Installs a fake Maven wrapper. SIFT prefers a project's own mvnw, so the
     * test exercises the real execution path without needing Maven or a network.
     */
    private void fakeMaven(String script) throws Exception {
        write(dir, "pom.xml", "<project/>");
        Path mvnw = dir.resolve("mvnw");
        Files.writeString(mvnw, "#!/bin/sh\n" + script + "\n");
        assertTrue(mvnw.toFile().setExecutable(true));
    }

    @Test void nothingRecognisedIsHealthyButSaysSo() {
        assertEquals(Main.OK, check());
        assertTrue(out().contains("no build or test command recognised"), out());
        assertTrue(out().contains("PROJECT HEALTHY"));
    }

    @Test void passingProjectIsHealthy() throws Exception {
        assumeFalse(System.getProperty("os.name").startsWith("Windows"));
        fakeMaven("echo 'Tests run: 12, Failures: 0, Errors: 0'");
        assertEquals(Main.OK, check());
        assertTrue(out().contains("✓ Build"), out());
        assertTrue(out().contains("✓ Tests (12 passed)"), out());
        assertTrue(out().contains("PROJECT HEALTHY"), out());
    }

    @Test void failingTestsShowEvidenceAndExitOne() throws Exception {
        assumeFalse(System.getProperty("os.name").startsWith("Windows"));
        // Succeeds for the compile step (-q), fails for the test run.
        fakeMaven("case \"$*\" in *-q*) exit 0;; esac\n"
                + "echo 'Tests run: 12, Failures: 2, Errors: 0'\n"
                + "echo '[ERROR]   RuleTest.rejectsEmpty:84 expected: <true>'\n"
                + "echo '[ERROR] -> [Help 1]'\n"
                + "exit 1");
        assertEquals(Main.PROBLEM, check());
        String o = out();
        assertTrue(o.contains("✗ Tests"), o);
        assertTrue(o.contains("10 passed, 2 failed"), o);
        assertTrue(o.contains("RuleTest.rejectsEmpty:84"), o);
        assertTrue(!o.contains("[Help 1]"), o);
        assertTrue(o.contains("PROJECT NEEDS ATTENTION"), o);
    }

    @Test void failedBuildSkipsTests() throws Exception {
        assumeFalse(System.getProperty("os.name").startsWith("Windows"));
        fakeMaven("echo '[ERROR] Foo.java:[3,1] cannot find symbol'\nexit 1");
        assertEquals(Main.PROBLEM, check());
        assertTrue(out().contains("✗ Build"), out());
        assertTrue(out().contains("cannot find symbol"), out());
        assertTrue(out().contains("- Tests"), out());
        assertTrue(out().contains("build failed"), out());
    }

    @Test void evidenceFallsBackToTailOfOutput() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 30; i++) {
            sb.append("line ").append(i).append('\n');
        }
        String e = CheckCommand.evidence(sb.toString());
        assertTrue(e.endsWith("line 30"));
        assertTrue(!e.contains("line 1\n"));
        assertEquals(12, e.lines().count());
    }
}
