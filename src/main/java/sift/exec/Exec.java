// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Sri Ganty

package sift.exec;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs an external program with an explicit argument list and captures its
 * output. Nothing goes through a shell, so arguments are never re-parsed.
 */
public final class Exec {

    public enum Outcome { EXITED, TIMED_OUT, NOT_FOUND }

    /** {@code exit} is only meaningful when {@code outcome} is {@link Outcome#EXITED}. */
    public record Result(Outcome outcome, int exit, String output) {
        public boolean ok() { return outcome == Outcome.EXITED && exit == 0; }
    }

    private Exec() {}

    /** Captures stdout only; stderr is discarded. For tools whose output SIFT parses. */
    public static Result stdout(Path dir, Duration timeout, List<String> command) {
        return run(dir, timeout, command, false);
    }

    /** Captures stdout and stderr interleaved, as a user would see them in a terminal. */
    public static Result combined(Path dir, Duration timeout, List<String> command) {
        return run(dir, timeout, command, true);
    }

    private static Result run(Path dir, Duration timeout, List<String> command, boolean mergeErr) {
        ProcessBuilder pb = new ProcessBuilder(command).directory(dir.toFile());
        if (mergeErr) {
            pb.redirectErrorStream(true);
        } else {
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        }
        // A child must never block waiting for keyboard input nobody will give.
        pb.redirectInput(ProcessBuilder.Redirect.from(nullDevice()));
        // A read-only git query must not take the index lock; otherwise running
        // SIFT could make a concurrent `git commit` in the user's editor fail.
        pb.environment().put("GIT_OPTIONAL_LOCKS", "0");

        Process p;
        try {
            p = pb.start();
        } catch (IOException e) {
            return new Result(Outcome.NOT_FOUND, -1, "");
        }

        // The deadline has to hold for a child that stays silent, so it is
        // enforced from a separate thread while this one blocks on the read.
        AtomicBoolean timedOut = new AtomicBoolean();
        Thread watchdog = new Thread(() -> {
            try {
                if (!p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    timedOut.set(true);
                    // Build tools fork workers that would otherwise keep the pipe open.
                    p.descendants().forEach(ProcessHandle::destroyForcibly);
                    p.destroyForcibly();
                }
            } catch (InterruptedException ignored) {
                // The process finished first; nothing to enforce.
            }
        }, "sift-exec-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();

        try {
            // Read before waiting: a full pipe buffer would block the child forever.
            String output;
            try (InputStream in = p.getInputStream()) {
                output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            int exit = p.waitFor();
            watchdog.interrupt();
            return timedOut.get() ? new Result(Outcome.TIMED_OUT, exit, output)
                                  : new Result(Outcome.EXITED, exit, output);
        } catch (IOException e) {
            return new Result(Outcome.NOT_FOUND, -1, "");
        } catch (InterruptedException e) {
            p.destroyForcibly();
            Thread.currentThread().interrupt();
            return new Result(Outcome.TIMED_OUT, -1, "");
        }
    }

    private static File nullDevice() {
        return new File(System.getProperty("os.name").startsWith("Windows") ? "NUL" : "/dev/null");
    }
}
