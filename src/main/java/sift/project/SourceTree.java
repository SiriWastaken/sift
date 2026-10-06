// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Sri Ganty

package sift.project;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Size and language mix of a project directory, found with one walk.
 *
 * <p>The walk never follows symlinks, skips dependency and build-output
 * directories, and stops after {@link #LIMIT} files, so running SIFT in a
 * home directory or a huge monorepo stays fast. The reported numbers are then
 * marked as a lower bound instead of being silently wrong.
 */
public final class SourceTree {

    static final int LIMIT = 100_000;

    private static final Set<String> SKIP = Set.of(
            ".git", "node_modules", "target", "build", "dist", "out", ".venv", "venv",
            "__pycache__", ".gradle", ".idea", ".next", ".mypy_cache", ".pytest_cache");

    private static final Map<String, String> LANGUAGES = Map.ofEntries(
            Map.entry("java", "Java"), Map.entry("kt", "Kotlin"), Map.entry("ts", "TypeScript"),
            Map.entry("tsx", "TypeScript"), Map.entry("js", "JavaScript"), Map.entry("jsx", "JavaScript"),
            Map.entry("mjs", "JavaScript"), Map.entry("py", "Python"), Map.entry("go", "Go"),
            Map.entry("rs", "Rust"), Map.entry("c", "C"), Map.entry("h", "C"), Map.entry("cpp", "C++"),
            Map.entry("cs", "C#"), Map.entry("rb", "Ruby"), Map.entry("swift", "Swift"), Map.entry("sh", "Shell"));

    /**
     * @param files      files seen (not directories)
     * @param bytes      their total size
     * @param languages  source file count per language name, sorted by name
     * @param truncated  true if the walk hit the limit, so the numbers are lower bounds
     */
    public record Stats(int files, long bytes, Map<String, Integer> languages, boolean truncated) {}

    private SourceTree() {}

    public static Stats scan(Path root) {
        int[] files = {0};
        long[] bytes = {0};
        boolean[] truncated = {false};
        Map<String, Integer> languages = new TreeMap<>();

        FileVisitor<Path> visitor = new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes a) {
                return !dir.equals(root) && SKIP.contains(dir.getFileName().toString())
                        ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes a) {
                if (!a.isRegularFile()) {
                    return FileVisitResult.CONTINUE; // symlinks and devices
                }
                if (files[0] == LIMIT) {
                    truncated[0] = true;
                    return FileVisitResult.TERMINATE;
                }
                files[0]++;
                bytes[0] += a.size();
                String name = file.getFileName().toString();
                int dot = name.lastIndexOf('.');
                String lang = dot < 0 ? null : LANGUAGES.get(name.substring(dot + 1).toLowerCase());
                if (lang != null) {
                    languages.merge(lang, 1, Integer::sum);
                }
                return FileVisitResult.CONTINUE;
            }

            // Unreadable entries are skipped; a permission error must not abort inspection.
            @Override public FileVisitResult visitFileFailed(Path file, IOException e) {
                return FileVisitResult.CONTINUE;
            }
        };
        try {
            Files.walkFileTree(root, visitor);
        } catch (IOException e) {
            // Only reachable for the root itself; report what was counted.
        }
        return new Stats(files[0], bytes[0], languages, truncated[0]);
    }
}
