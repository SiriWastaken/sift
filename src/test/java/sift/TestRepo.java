// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Sri Ganty

package sift;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Builds throwaway git repositories for tests. */
public final class TestRepo {

    private TestRepo() {}

    public static void git(Path dir, String... args) throws IOException, InterruptedException {
        String[] cmd = new String[args.length + 1];
        cmd[0] = "git";
        System.arraycopy(args, 0, cmd, 1, args.length);
        Process p = new ProcessBuilder(cmd).directory(dir.toFile())
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        assertEquals(0, p.waitFor(), String.join(" ", cmd));
    }

    public static void init(Path dir) throws Exception {
        git(dir, "init", "-q", "-b", "main");
        // Identity set per-repo so tests do not depend on global config.
        git(dir, "config", "user.email", "test@example.com");
        git(dir, "config", "user.name", "Test");
        git(dir, "config", "commit.gpgsign", "false");
    }

    public static void write(Path dir, String file, String content) throws IOException {
        Path p = dir.resolve(file);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }

    public static void commitAll(Path dir, String message) throws Exception {
        git(dir, "add", "-A");
        git(dir, "commit", "-q", "-m", message);
    }
}
