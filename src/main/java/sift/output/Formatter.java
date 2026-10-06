// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Sri Ganty

package sift.output;

import java.util.List;
import java.util.Map;

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
}
