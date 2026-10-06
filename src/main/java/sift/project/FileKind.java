// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Sri Ganty

package sift.project;

import java.util.Locale;
import java.util.Set;

/**
 * Coarse role of a file, decided from its path alone. This is a naming
 * convention check, not an analysis: it never opens the file.
 */
public enum FileKind {
    TEST("test"), SOURCE("source"), CONFIG("config"), DOCS("docs"), OTHER("other");

    private static final Set<String> SOURCE_EXT = Set.of(
            "java", "kt", "scala", "groovy", "ts", "tsx", "js", "jsx", "mjs", "cjs", "vue", "svelte",
            "py", "go", "rs", "c", "h", "cc", "cpp", "hpp", "cs", "rb", "php", "swift", "sh");
    private static final Set<String> CONFIG_EXT = Set.of(
            "json", "yml", "yaml", "toml", "xml", "properties", "gradle", "kts", "cfg", "ini", "lock");
    private static final Set<String> CONFIG_NAMES = Set.of(
            ".gitignore", ".gitattributes", ".editorconfig", "dockerfile", "makefile", "mvnw", "gradlew");
    private static final Set<String> TEST_DIRS = Set.of("test", "tests", "__tests__", "spec", "specs");

    private final String label;

    FileKind(String label) { this.label = label; }

    public String label() { return label; }

    /** @param path repository-relative path with forward slashes, as git prints it */
    public static FileKind of(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        String[] segments = lower.split("/");
        String name = segments[segments.length - 1];
        String rawName = path.substring(path.lastIndexOf('/') + 1);

        // Docs first, so that tests/README.md is documentation, not a test.
        if (name.endsWith(".md") || name.endsWith(".rst")
                || name.startsWith("license") || segments[0].equals("docs")) {
            return DOCS;
        }
        for (int i = 0; i < segments.length - 1; i++) {
            if (TEST_DIRS.contains(segments[i])) {
                return TEST;
            }
        }
        if (isTestName(rawName)) {
            return TEST;
        }
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "";
        if (CONFIG_NAMES.contains(name) || CONFIG_EXT.contains(ext)) {
            return CONFIG;
        }
        return SOURCE_EXT.contains(ext) ? SOURCE : OTHER;
    }

    // FooTest.java, FooTests.cs, test_foo.py, foo_test.go, foo.test.ts, foo.spec.ts.
    // The Java-style suffix is case-sensitive so "latest.py" is not a test.
    private static boolean isTestName(String name) {
        String stem = name.contains(".") ? name.substring(0, name.indexOf('.')) : name;
        String lowerName = name.toLowerCase(Locale.ROOT);
        return stem.length() > 4 && stem.endsWith("Test") || stem.length() > 5 && stem.endsWith("Tests")
                || stem.startsWith("test_") || stem.endsWith("_test")
                || lowerName.contains(".test.") || lowerName.contains(".spec.");
    }
}
