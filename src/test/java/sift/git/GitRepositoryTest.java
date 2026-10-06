// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Sri Ganty

package sift.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import sift.git.GitRepository.State;

class GitRepositoryTest {

    @TempDir Path dir;

    private void git(String... args) throws IOException, InterruptedException {
        String[] cmd = new String[args.length + 1];
        cmd[0] = "git";
        System.arraycopy(args, 0, cmd, 1, args.length);
        Process p = new ProcessBuilder(cmd).directory(dir.toFile())
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        assertEquals(0, p.waitFor(), String.join(" ", cmd));
    }

    private void initRepo() throws Exception {
        git("init", "-q", "-b", "main");
        // Identity set per-repo so the test does not depend on global config.
        git("config", "user.email", "test@example.com");
        git("config", "user.name", "Test");
        git("config", "commit.gpgsign", "false");
    }

    @Test void notARepository() {
        assertEquals(Optional.empty(), GitRepository.read(dir));
    }

    @Test void gitUnavailable() {
        assertEquals(Optional.empty(), GitRepository.read("sift-no-such-executable", dir));
    }

    @Test void cleanRepository() throws Exception {
        initRepo();
        Files.writeString(dir.resolve("a.txt"), "a");
        git("add", ".");
        git("commit", "-q", "-m", "init");

        State s = GitRepository.read(dir).orElseThrow();
        assertEquals("main", s.branch());
        assertTrue(s.clean());
    }

    @Test void modifiedAndUntrackedFiles() throws Exception {
        initRepo();
        Files.writeString(dir.resolve("a.txt"), "a");
        git("add", ".");
        git("commit", "-q", "-m", "init");
        Files.writeString(dir.resolve("a.txt"), "changed");
        Files.writeString(dir.resolve("new.txt"), "new");

        State s = GitRepository.read(dir).orElseThrow();
        assertEquals(1, s.changed());
        assertEquals(1, s.untracked());
        assertFalse(s.clean());
    }

    @Test void freshRepositoryWithoutCommitsStillReportsBranch() throws Exception {
        initRepo();
        assertEquals("main", GitRepository.read(dir).orElseThrow().branch());
    }

    @Test void parseCountsEachEntryKind() {
        String out = """
                # branch.oid abc
                # branch.head feature/x
                1 .M N... 100644 100644 100644 a b src/A.java
                2 R. N... 100644 100644 100644 a b R100 new.txt\told.txt
                u UU N... 100644 100644 100644 100644 a b c conflict.txt
                ? scratch.txt
                """;
        assertEquals(new State("feature/x", 3, 1, 1, 0, 0), GitRepository.parse(out));
    }
}
