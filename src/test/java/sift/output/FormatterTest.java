// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Sri Ganty

package sift.output;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import sift.checks.CheckResult;

class FormatterTest {

    @Test void reportAlignsValues() {
        String r = Formatter.report("T", List.of(Map.entry("a", "1"), Map.entry("long", "2")));
        assertEquals("T\n\na     1\nlong  2\n", r.replace("\r", ""));
    }

    @Test void resultIndentsEvidenceUnderTheName() {
        String r = Formatter.result(CheckResult.fail("Tests", "2 failed\nsee A.java:3"));
        assertEquals("✗ Tests\n  2 failed\n  see A.java:3\n\n", r);
        assertEquals("✓ Build\n", Formatter.result(CheckResult.pass("Build")));
    }

    @Test void sizesUseDecimalUnits() {
        assertEquals("0 B", Formatter.size(0));
        assertEquals("999 B", Formatter.size(999));
        assertEquals("1.5 kB", Formatter.size(1500));
        assertEquals("1.0 MB", Formatter.size(999_999));
        assertEquals("12.4 MB", Formatter.size(12_400_000));
    }

    @Test void pluralizes() {
        assertEquals("1 file", Formatter.count(1, "file"));
        assertEquals("0 files", Formatter.count(0, "file"));
    }
}
