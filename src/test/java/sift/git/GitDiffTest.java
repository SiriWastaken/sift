// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Sri Ganty

package sift.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static sift.TestRepo.commitAll;
import static sift.TestRepo.git;
import static sift.TestRepo.init;
import static sift.TestRepo.write;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import sift.git.GitDiff.FileChange;
import sift.git.GitDiff.Kind;

class GitDiffTest {

    @TempDir Path dir;

    private FileChange find(List<FileChange> all, String path) {
        return all.stream().filter(c -> c.path().equals(path)).findFirst().orElseThrow();
    }

    @Test void parsesCountsKindsAndRenames() {
        String numstat = "3\t1\tsrc/A.java\0" + "0\t0\t\0old.txt\0new.txt\0" + "-\t-\timg.png\0";
        String status = "M\0src/A.java\0" + "R100\0old.txt\0new.txt\0" + "A\0img.png\0";
        List<FileChange> c = GitDiff.parse(numstat, status);

        assertEquals(3, c.size());
        FileChange a = find(c, "src/A.java");
        assertEquals(Kind.MODIFIED, a.kind());
        assertEquals(3, a.added());
        assertEquals(1, a.deleted());
        FileChange r = find(c, "new.txt");
        assertEquals(Kind.RENAMED, r.kind());
        assertEquals("old.txt", r.oldPath());
        assertTrue(find(c, "img.png").binary());
    }

    @Test void readsRealRepository() throws Exception {
        init(dir);
        write(dir, "keep.txt", "1\n2\n3\n");
        write(dir, "gone.txt", "x\n");
        write(dir, "move.txt", "same content here\nline two\nline three\n");
        commitAll(dir, "init");

        write(dir, "keep.txt", "1\n2\n3\n4\n5\n");
        Files.delete(dir.resolve("gone.txt"));
        git(dir, "mv", "move.txt", "moved.txt");
        write(dir, "added.txt", "a\n");
        git(dir, "add", "added.txt");
        write(dir, "scratch.txt", "untracked");

        List<FileChange> c = GitDiff.read(dir, null, false).orElseThrow();
        assertEquals(2, find(c, "keep.txt").added());
        assertEquals(Kind.DELETED, find(c, "gone.txt").kind());
        assertEquals(Kind.RENAMED, find(c, "moved.txt").kind());
        assertEquals(Kind.ADDED, find(c, "added.txt").kind());
        assertEquals(List.of("scratch.txt"), GitDiff.untracked(dir).orElseThrow());
    }

    @Test void stagedOnlyShowsTheIndex() throws Exception {
        init(dir);
        write(dir, "a.txt", "a\n");
        commitAll(dir, "init");
        write(dir, "a.txt", "a\nb\n");
        git(dir, "add", "a.txt");
        write(dir, "a.txt", "a\nb\nc\n");

        assertEquals(1, find(GitDiff.read(dir, null, true).orElseThrow(), "a.txt").added());
        assertEquals(2, find(GitDiff.read(dir, null, false).orElseThrow(), "a.txt").added());
    }

    @Test void repositoryWithoutCommitsFallsBackToIndex() throws Exception {
        init(dir);
        write(dir, "a.txt", "a\n");
        git(dir, "add", "a.txt");
        assertEquals(Kind.ADDED, find(GitDiff.read(dir, null, false).orElseThrow(), "a.txt").kind());
    }

    @Test void unknownRevisionIsEmpty() throws Exception {
        init(dir);
        write(dir, "a.txt", "a\n");
        commitAll(dir, "init");
        assertTrue(GitDiff.read(dir, "no-such-branch", false).isEmpty());
    }
}
