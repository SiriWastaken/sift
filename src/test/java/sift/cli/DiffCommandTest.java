// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Sri Ganty

package sift.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static sift.TestRepo.commitAll;
import static sift.TestRepo.init;
import static sift.TestRepo.write;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import sift.Main;
import sift.git.GitDiff.FileChange;
import sift.git.GitDiff.Kind;

class DiffCommandTest {

    @TempDir Path dir;

    private final ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errBuf = new ByteArrayOutputStream();

    private int run(String... args) {
        return Main.run(prepend(args), dir,
                new PrintStream(outBuf, true, StandardCharsets.UTF_8),
                new PrintStream(errBuf, true, StandardCharsets.UTF_8));
    }

    private static String[] prepend(String[] args) {
        String[] all = new String[args.length + 1];
        all[0] = "diff";
        System.arraycopy(args, 0, all, 1, args.length);
        return all;
    }

    private String out() { return outBuf.toString(StandardCharsets.UTF_8); }
    private String err() { return errBuf.toString(StandardCharsets.UTF_8); }

    @Test void outsideRepositoryIsAProblem() {
        assertEquals(Main.PROBLEM, run());
        assertTrue(err().contains("Not a git repository"));
    }

    @Test void optionLikeRevisionIsRejected() throws Exception {
        init(dir);
        assertEquals(Main.USAGE, run("--output=/tmp/x"));
        assertEquals(Main.USAGE, run("a", "b"));
    }

    @Test void stagedCannotTakeARevision() throws Exception {
        init(dir);
        assertEquals(Main.USAGE, run("--staged", "main"));
    }

    @Test void unknownRevisionIsAProblem() throws Exception {
        init(dir);
        write(dir, "a.txt", "a\n");
        commitAll(dir, "init");
        assertEquals(Main.PROBLEM, run("nope"));
        assertTrue(err().contains("nope"));
    }

    @Test void cleanTreeSaysSo() throws Exception {
        init(dir);
        write(dir, "a.txt", "a\n");
        commitAll(dir, "init");
        assertEquals(Main.OK, run());
        assertTrue(out().contains("No changes"));
    }

    @Test void summarizesRealChanges() throws Exception {
        init(dir);
        write(dir, "src/Rule.java", "class Rule {}\n");
        commitAll(dir, "init");
        write(dir, "src/Rule.java", "class Rule {}\n// more\n");
        write(dir, "src/RuleTest.java", "class RuleTest {}\n");
        write(dir, "notes.md", "hi");

        assertEquals(Main.OK, run());
        String o = out();
        assertTrue(o.contains("M  +1  -0  src/Rule.java"), o);
        assertTrue(o.contains("?"), o);
        assertTrue(o.contains("2 untracked"), o);
    }

    @Test void renderGroupsByRoleAndNotesMissingTests() {
        var changes = List.of(
                new FileChange("src/A.java", null, Kind.MODIFIED, 10, 2, false),
                new FileChange("src/B.java", null, Kind.MODIFIED, 5, 1, false),
                new FileChange("pom.xml", null, Kind.MODIFIED, 1, 1, false));
        String o = DiffCommand.render("working tree vs HEAD", changes, List.of());
        assertTrue(o.contains("3 files changed"), o);
        assertTrue(o.contains("2 source files modified"), o);
        assertTrue(o.contains("1 config file modified"), o);
        assertTrue(o.contains("16 lines added, 4 removed"), o);
        assertTrue(o.contains("note: source changed but no test file did"), o);
    }

    @Test void noMissingTestsNoteWhenTestsChanged() {
        var changes = List.of(
                new FileChange("src/A.java", null, Kind.MODIFIED, 1, 0, false),
                new FileChange("src/ATest.java", null, Kind.ADDED, 9, 0, false));
        assertFalse(DiffCommand.render("x", changes, List.of()).contains("no test file"));
    }

    @Test void lockfileMismatchIsNoted() {
        var onlyManifest = List.of(new FileChange("package.json", null, Kind.MODIFIED, 1, 0, false));
        assertTrue(DiffCommand.render("x", onlyManifest, List.of()).contains("no lockfile did"));
        var onlyLock = List.of(new FileChange("pnpm-lock.yaml", null, Kind.MODIFIED, 1, 0, false));
        assertTrue(DiffCommand.render("x", onlyLock, List.of()).contains("package.json did not"));
    }

    @Test void longListsAreCutButSummaryStaysComplete() {
        var changes = new java.util.ArrayList<FileChange>();
        for (int i = 0; i < 50; i++) {
            changes.add(new FileChange(String.format("src/F%02d.java", i), null, Kind.MODIFIED, 1, 0, false));
        }
        String o = DiffCommand.render("x", changes, List.of());
        assertTrue(o.contains("... and 10 more"), o);
        assertTrue(o.contains("50 source files modified"), o);
    }
}
