// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Sri Ganty

package sift.project;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import sift.project.FileKind;

class FileKindTest {

    @ParameterizedTest
    @CsvSource({
            "src/main/java/sift/Main.java,       SOURCE",
            "src/test/java/sift/MainTest.java,   TEST",
            "tests/test_api.py,                  TEST",
            "pkg/foo_test.go,                    TEST",
            "web/app.spec.ts,                    TEST",
            "web/app.ts,                         SOURCE",
            "pom.xml,                            CONFIG",
            "package-lock.json,                  CONFIG",
            "requirements.txt,                   OTHER",
            "README.md,                          DOCS",
            "tests/README.md,                    DOCS",
            "docs/guide.png,                     DOCS",
            "LICENSE,                            DOCS",
            "src/latest.py,                      SOURCE",
            "assets/logo.png,                    OTHER",
    })
    void classifiesByPath(String path, FileKind expected) {
        assertEquals(expected, FileKind.of(path));
    }
}
