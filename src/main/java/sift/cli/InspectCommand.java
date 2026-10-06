// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Sri Ganty

package sift.cli;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import sift.Main;
import sift.git.GitRepository;
import sift.output.Formatter;
import sift.project.Project;
import sift.project.ProjectDetector;

/** {@code sift inspect}: answers "what am I looking at?". */
public final class InspectCommand {

    private InspectCommand() {}

    public static int run(Path dir, PrintStream out) {
        Project project = ProjectDetector.detect(dir);

        List<Map.Entry<String, String>> rows = new ArrayList<>();
        rows.add(Map.entry("project", project.name()));
        rows.add(Map.entry("language", project.language().label()));
        rows.add(Map.entry("build", project.buildSystem().label()));

        GitRepository.read(dir).ifPresentOrElse(git -> {
            rows.add(Map.entry("git", git.branch()));
            rows.add(Map.entry("status", git.describe()));
        }, () -> rows.add(Map.entry("git", "none")));

        out.print(Formatter.report("SIFT", rows));
        return Main.OK;
    }
}
