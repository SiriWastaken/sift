// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Sri Ganty

package sift.cli;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import sift.Main;
import sift.git.GitDiff;
import sift.git.GitDiff.FileChange;
import sift.git.GitDiff.Kind;
import sift.git.GitRepository;
import sift.output.Formatter;
import sift.project.FileKind;

/**
 * {@code sift diff [--staged] [<revision>]}: answers "what did I change?".
 *
 * <p>Git stays the source of truth: every number comes from git. SIFT adds the
 * two things git's own output leaves out: untracked files, and a grouping of
 * the changes by the role of each file.
 */
public final class DiffCommand {

    /** Past this many rows the file list is cut; the summary below it always counts everything. */
    private static final int MAX_ROWS = 40;

    private DiffCommand() {}

    public static int run(List<String> args, Path dir, PrintStream out, PrintStream err) {
        boolean staged = false;
        String revision = null;
        for (String a : args) {
            if (a.equals("--staged") || a.equals("--cached")) {
                staged = true;
            } else if (a.startsWith("-") || revision != null) {
                // Rejecting leading dashes also stops a "revision" from being read as a git option.
                err.println("Unexpected argument: " + a);
                err.println();
                err.println("Usage: sift diff [--staged] [<revision>]");
                return Main.USAGE;
            } else {
                revision = a;
            }
        }
        if (staged && revision != null) {
            err.println("--staged compares the index with HEAD and cannot be combined with a revision.");
            return Main.USAGE;
        }

        if (GitRepository.read(dir).isEmpty()) {
            err.println("Not a git repository (or git is not installed).");
            return Main.PROBLEM;
        }
        Optional<List<FileChange>> changes = GitDiff.read(dir, revision, staged);
        if (changes.isEmpty()) {
            err.println("git could not diff against '" + revision + "'. Is it a valid revision?");
            return Main.PROBLEM;
        }

        // A range such as a..b compares two commits; the working tree, and so
        // its untracked files, is not part of it.
        boolean workingTree = !staged && (revision == null || !revision.contains(".."));
        List<String> untracked = workingTree ? GitDiff.untracked(dir).orElse(List.of()) : List.of();

        String against = revision != null ? revision : "HEAD";
        String comparing = staged ? "index vs " + against
                : workingTree ? "working tree vs " + against : against;
        out.print(render(comparing, changes.get(), untracked));
        return Main.OK;
    }

    static String render(String comparing, List<FileChange> changes, List<String> untracked) {
        if (changes.isEmpty() && untracked.isEmpty()) {
            return "SIFT DIFF\n\nNo changes (" + comparing + ").\n";
        }
        StringBuilder sb = new StringBuilder("SIFT DIFF\n\n").append(comparing).append("\n\n");

        List<String> parts = new ArrayList<>();
        if (!changes.isEmpty()) {
            parts.add(Formatter.count(changes.size(), "file") + " changed");
        }
        if (!untracked.isEmpty()) {
            parts.add(untracked.size() + " untracked");
        }
        String headline = String.join(", ", parts);
        sb.append(headline).append("\n\n");

        int pad = Math.max(String.valueOf(changes.stream().mapToInt(FileChange::added).max().orElse(0)).length(),
                           String.valueOf(changes.stream().mapToInt(FileChange::deleted).max().orElse(0)).length());
        int width = 2 * pad + 4; // "+NN  -NN"
        List<String> rows = new ArrayList<>();
        for (FileChange c : changes) {
            String counts = c.binary() ? String.format("%-" + width + "s", "binary")
                    : String.format("+%" + pad + "d  -%" + pad + "d", c.added(), c.deleted());
            rows.add(letter(c.kind()) + "  " + counts + "  "
                    + (c.oldPath() != null ? c.oldPath() + " -> " + c.path() : c.path()));
        }
        for (String path : untracked) {
            rows.add("?  " + String.format("%-" + width + "s", "new") + "  " + path);
        }
        rows.stream().limit(MAX_ROWS).forEach(r -> sb.append("  ").append(r).append('\n'));
        if (rows.size() > MAX_ROWS) {
            sb.append("  ... and ").append(rows.size() - MAX_ROWS).append(" more\n");
        }

        sb.append("\nSummary\n\n");
        for (String line : summary(changes, untracked)) {
            sb.append("  ").append(line).append('\n');
        }
        return sb.toString();
    }

    private static List<String> summary(List<FileChange> changes, List<String> untracked) {
        // role -> how it changed -> count, in a stable display order.
        Map<FileKind, Map<Kind, Integer>> byRole = new EnumMap<>(FileKind.class);
        for (FileChange c : changes) {
            byRole.computeIfAbsent(FileKind.of(c.path()), k -> new EnumMap<>(Kind.class)).merge(c.kind(), 1, Integer::sum);
        }

        List<String> lines = new ArrayList<>();
        for (var role : byRole.entrySet()) {
            for (var kind : role.getValue().entrySet()) {
                lines.add(kind.getValue() + " " + role.getKey().label() + " file" + (kind.getValue() == 1 ? "" : "s")
                        + " " + kind.getKey().name().toLowerCase());
            }
        }
        if (!untracked.isEmpty()) {
            lines.add(Formatter.count(untracked.size(), "untracked file") + " (line counts not included)");
        }
        if (!changes.isEmpty()) {
            int added = changes.stream().mapToInt(FileChange::added).sum();
            int deleted = changes.stream().mapToInt(FileChange::deleted).sum();
            lines.add(Formatter.count(added, "line") + " added, " + deleted + " removed");
        }

        // Notes state only what the file list proves, never what the code means.
        boolean source = byRole.containsKey(FileKind.SOURCE);
        if (source && !byRole.containsKey(FileKind.TEST)
                && untracked.stream().noneMatch(p -> FileKind.of(p) == FileKind.TEST)) {
            lines.add("note: source changed but no test file did");
        }
        boolean manifest = changes.stream().anyMatch(c -> c.path().equals("package.json"));
        boolean lock = changes.stream().anyMatch(c -> c.path().matches("(.*/)?(package-lock\\.json|pnpm-lock\\.yaml|yarn\\.lock)"));
        if (manifest && !lock) {
            lines.add("note: package.json changed but no lockfile did");
        } else if (lock && !manifest) {
            lines.add("note: a lockfile changed but package.json did not");
        }
        return lines;
    }

    private static char letter(Kind k) {
        return switch (k) {
            case ADDED -> 'A';
            case MODIFIED -> 'M';
            case DELETED -> 'D';
            case RENAMED -> 'R';
        };
    }
}
