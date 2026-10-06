// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Sri Ganty

package sift.git;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** A compact, parsed view of {@code git diff}, built from NUL-delimited output. */
public final class GitDiff {

    public enum Kind { ADDED, MODIFIED, DELETED, RENAMED }

    /**
     * @param added   lines added; 0 for binary files, which git does not count
     * @param deleted lines removed; 0 for binary files
     * @param path    the new path (for a rename, the destination)
     * @param oldPath the previous path for a rename, otherwise null
     */
    public record FileChange(String path, String oldPath, Kind kind, int added, int deleted, boolean binary) {}

    private GitDiff() {}

    /**
     * Changes between {@code revision} and the working tree, or between the
     * index and HEAD when {@code staged}. Null {@code revision} means HEAD.
     * Empty when git fails, e.g. for an unknown revision.
     */
    public static Optional<List<FileChange>> read(Path dir, String revision, boolean staged) {
        List<String> base = new ArrayList<>(List.of("diff", "-z"));
        if (staged) {
            base.add("--cached");
        }
        if (revision != null) {
            base.add(revision);
        } else if (!staged) {
            // Plain `git diff` compares the index with the working tree and so
            // hides everything already staged. "What did I change?" means
            // against the last commit, staged or not.
            if (GitRepository.run(dir, "rev-parse", "--verify", "-q", "HEAD").isPresent()) {
                base.add("HEAD");
            } else {
                base.add("--cached"); // no commits yet: the index is all there is
            }
        }
        // "--" ends revisions so a revision name can never be read as a path.
        base.add("--");

        // numstat gives line counts, name-status gives the kind of change. Git
        // has no single -z format with both, so the two runs are joined by path.
        Optional<String> numstat = GitRepository.run(dir, insert(base, "--numstat"));
        Optional<String> status = GitRepository.run(dir, insert(base, "--name-status"));
        if (numstat.isEmpty() || status.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(parse(numstat.get(), status.get()));
    }

    /** Untracked, non-ignored files: the part of "what changed" that git diff never shows. */
    public static Optional<List<String>> untracked(Path dir) {
        return GitRepository.run(dir, "ls-files", "--others", "--exclude-standard", "-z")
                .map(out -> Arrays.stream(out.split("\0")).filter(s -> !s.isEmpty()).toList());
    }

    private static String[] insert(List<String> base, String flag) {
        List<String> args = new ArrayList<>(base);
        args.add(1, flag);
        return args.toArray(new String[0]);
    }

    static List<FileChange> parse(String numstat, String nameStatus) {
        // name-status first: path -> kind (and rename source).
        var kinds = new java.util.LinkedHashMap<String, Kind>();
        var sources = new java.util.HashMap<String, String>();
        String[] ns = nameStatus.split("\0");
        for (int i = 0; i < ns.length && !ns[i].isEmpty(); ) {
            char code = ns[i].charAt(0);
            if (code == 'R' || code == 'C') {
                // "R100", old, new. A copy leaves its source in place, so it is an addition.
                kinds.put(ns[i + 2], code == 'R' ? Kind.RENAMED : Kind.ADDED);
                if (code == 'R') {
                    sources.put(ns[i + 2], ns[i + 1]);
                }
                i += 3;
            } else {
                kinds.put(ns[i + 1], switch (code) {
                    case 'A' -> Kind.ADDED;
                    case 'D' -> Kind.DELETED;
                    default -> Kind.MODIFIED; // M, T (type change), U
                });
                i += 2;
            }
        }

        var counts = new java.util.HashMap<String, int[]>();
        var binary = new java.util.HashSet<String>();
        String[] parts = numstat.split("\0");
        for (int i = 0; i < parts.length && !parts[i].isEmpty(); ) {
            String[] f = parts[i].split("\t", 3);
            // A rename is "added\tdeleted\t" then old and new as separate fields.
            String path = f[2].isEmpty() ? parts[i + 2] : f[2];
            i += f[2].isEmpty() ? 3 : 1;
            if (f[0].equals("-")) {
                binary.add(path); // binary files report "-" for both counts
            } else {
                counts.put(path, new int[] {Integer.parseInt(f[0]), Integer.parseInt(f[1])});
            }
        }

        List<FileChange> changes = new ArrayList<>();
        for (var e : kinds.entrySet()) {
            int[] c = counts.getOrDefault(e.getKey(), new int[2]);
            changes.add(new FileChange(e.getKey(), sources.get(e.getKey()), e.getValue(),
                    c[0], c[1], binary.contains(e.getKey())));
        }
        changes.sort(java.util.Comparator.comparing(FileChange::path));
        return changes;
    }
}
