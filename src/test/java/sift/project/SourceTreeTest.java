// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Sri Ganty

package sift.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static sift.TestRepo.write;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceTreeTest {

    @TempDir Path dir;

    @Test void countsFilesBytesAndLanguages() throws Exception {
        write(dir, "A.java", "12345");
        write(dir, "src/B.java", "123");
        write(dir, "tool.py", "12");
        write(dir, "notes.txt", "1");
        SourceTree.Stats s = SourceTree.scan(dir);
        assertEquals(4, s.files());
        assertEquals(11, s.bytes());
        assertEquals(2, s.languages().get("Java"));
        assertEquals(1, s.languages().get("Python"));
        assertFalse(s.truncated());
    }

    @Test void skipsDependencyAndBuildDirectories() throws Exception {
        write(dir, "A.java", "x");
        write(dir, "node_modules/lib/index.js", "x");
        write(dir, "target/classes/A.class", "x");
        write(dir, ".git/config", "x");
        write(dir, ".venv/lib/site.py", "x");
        SourceTree.Stats s = SourceTree.scan(dir);
        assertEquals(1, s.files());
        assertEquals(java.util.Map.of("Java", 1), s.languages());
    }

    @Test void rootNamedLikeASkippedDirectoryIsStillScanned() throws Exception {
        Path root = dir.resolve("build");
        write(root, "A.java", "x");
        assertEquals(1, SourceTree.scan(root).files());
    }

    @Test void emptyDirectory() {
        assertEquals(0, SourceTree.scan(dir).files());
    }
}
