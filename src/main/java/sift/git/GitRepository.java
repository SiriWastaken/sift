// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Sri Ganty

package sift.git;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import sift.exec.Exec;

/**
 * Reads repository state by asking the {@code git} executable.
 *
 * <p>Git is queried through its CLI rather than by parsing {@code .git}
 * internals. That keeps SIFT correct with worktrees, packed refs, submodules
 * and future storage formats, at the cost of one subprocess per query.
 */
public final class GitRepository {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    /**
     * @param branch    current branch, or "(detached)" when HEAD is not on one
     * @param changed   tracked files with staged or unstaged changes, or conflicts
     * @param untracked files not yet known to git (and not ignored)
     * @param conflicts files with unresolved merge conflicts (a subset of {@code changed})
     * @param ahead     commits not yet on the upstream; 0 when there is no upstream
     * @param behind    upstream commits not yet merged; 0 when there is no upstream
     */
    public record State(String branch, int changed, int untracked, int conflicts, int ahead, int behind) {
        public boolean clean() { return changed == 0 && untracked == 0; }

        /** "clean", "2 changed", or "2 changed, 1 untracked". */
        public String describe() {
            if (clean()) {
                return "clean";
            }
            List<String> parts = new ArrayList<>();
            if (changed > 0) {
                parts.add(changed + " changed");
            }
            if (untracked > 0) {
                parts.add(untracked + " untracked");
            }
            return String.join(", ", parts);
        }
    }

    private GitRepository() {}

    /** Empty when {@code dir} is not in a repository or git cannot be run. */
    public static Optional<State> read(Path dir) {
        return read("git", dir);
    }

    // The executable is a parameter only so tests can simulate a missing git.
    static Optional<State> read(String executable, Path dir) {
        // porcelain v2 is a stable, machine-oriented format; --branch adds the
        // branch header so one process answers both questions.
        Exec.Result r = Exec.stdout(dir, TIMEOUT, List.of(executable, "status", "--porcelain=v2", "--branch"));
        return r.ok() ? Optional.of(parse(r.output())) : Optional.empty();
    }

    /**
     * Runs {@code git <args>} in {@code dir} and returns stdout. Empty when git
     * exits non-zero, is missing or times out; callers that need to tell those
     * apart should not use this.
     */
    public static Optional<String> run(Path dir, String... args) {
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        cmd.addAll(Arrays.asList(args));
        Exec.Result r = Exec.stdout(dir, TIMEOUT, cmd);
        return r.ok() ? Optional.of(r.output()) : Optional.empty();
    }

    static State parse(String porcelainV2) {
        String branch = "(detached)";
        int changed = 0;
        int untracked = 0;
        int conflicts = 0;
        int ahead = 0;
        int behind = 0;
        for (String line : porcelainV2.split("\n")) {
            if (line.startsWith("# branch.head ")) {
                // Git prints the literal "(detached)" itself.
                branch = line.substring("# branch.head ".length()).trim();
            } else if (line.startsWith("# branch.ab ")) {
                // Only present when an upstream is configured: "+<ahead> -<behind>".
                String[] ab = line.substring("# branch.ab ".length()).trim().split(" ");
                ahead = Integer.parseInt(ab[0].substring(1));
                behind = Integer.parseInt(ab[1].substring(1));
            } else if (line.startsWith("? ")) {
                untracked++;
            } else if (line.startsWith("u ")) {
                changed++;
                conflicts++;
            } else if (line.startsWith("1 ") || line.startsWith("2 ")) {
                changed++; // ordinary change, rename/copy
            }
        }
        return new State(branch, changed, untracked, conflicts, ahead, behind);
    }
}
