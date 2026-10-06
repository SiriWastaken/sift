// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Sri Ganty

package sift.output;

import java.util.List;
import java.util.Map;

import sift.checks.CheckResult;
import sift.checks.Status;

/** Plain-text layout helpers shared by all commands. */
public final class Formatter {

    private Formatter() {}

    /**
     * Renders a title followed by aligned label/value rows. No color and no
     * escape codes: the output reads the same in a pager, a log or CI.
     */
    public static String report(String title, List<Map.Entry<String, String>> rows) {
        int width = rows.stream().mapToInt(r -> r.getKey().length()).max().orElse(0);
        StringBuilder sb = new StringBuilder(title).append("\n\n");
        for (var row : rows) {
            sb.append(String.format("%-" + (width + 2) + "s%s%n", row.getKey(), row.getValue()));
        }
        return sb.toString();
    }

    /**
     * One result as a marked line plus its evidence indented underneath. The
     * mark is a distinct glyph per status, so meaning never depends on color.
     */
    public static String result(CheckResult r) {
        StringBuilder sb = new StringBuilder(mark(r.status())).append(' ').append(r.name()).append('\n');
        if (!r.detail().isEmpty()) {
            r.detail().lines().forEach(l -> sb.append("  ").append(l).append('\n'));
            sb.append('\n');
        }
        return sb.toString();
    }

    public static String mark(Status s) {
        return switch (s) {
            case PASS -> "✓";
            case WARN -> "!";
            case FAIL -> "✗";
            case SKIP -> "-";
        };
    }

    /** "1 file", "3 files". */
    public static String count(int n, String noun) {
        return n + " " + noun + (n == 1 ? "" : "s");
    }
}
