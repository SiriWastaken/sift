// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Sri Ganty

package sift.cli;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import sift.Main;
import sift.git.GitRepository;
import sift.output.Formatter;
import sift.project.Node;
import sift.project.Project;
import sift.project.Project.BuildSystem;
import sift.project.ProjectDetector;
import sift.project.SourceTree;
import sift.project.Stack;

/** {@code sift inspect}: answers "what am I looking at?". */
public final class InspectCommand {

    private InspectCommand() {}

    public static int run(Path dir, PrintStream out) {
        Project project = ProjectDetector.detect(dir);
        SourceTree.Stats tree = SourceTree.scan(project.root());

        List<Map.Entry<String, String>> rows = new ArrayList<>();
        rows.add(Map.entry("project", project.name()));
        rows.add(Map.entry("language", project.language().label()));

        // Languages beyond the primary one, most files first. Shown only when
        // present: an empty "also" row would say nothing.
        String also = tree.languages().entrySet().stream()
                .filter(e -> !e.getKey().equals(project.language().label()))
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3).map(Map.Entry::getKey).collect(Collectors.joining(", "));
        if (!also.isEmpty()) {
            rows.add(Map.entry("also", also));
        }

        rows.add(Map.entry("framework", Stack.framework(project).orElse("unknown")));
        rows.add(Map.entry("build", project.buildSystem().label()));
        if (project.buildSystem() == BuildSystem.NPM) {
            rows.add(Map.entry("package", Node.packageManager(project.root())));
        }
        rows.add(Map.entry("tests", Stack.testFramework(project).orElse("unknown")));
        rows.add(Map.entry("files", describe(tree)));

        GitRepository.read(dir).ifPresentOrElse(git -> {
            rows.add(Map.entry("git", git.branch()));
            rows.add(Map.entry("status", git.describe()));
        }, () -> rows.add(Map.entry("git", "none")));

        out.print(Formatter.report("SIFT", rows));
        return Main.OK;
    }

    private static String describe(SourceTree.Stats t) {
        return String.format(java.util.Locale.ROOT, "%,d%s (%s)", t.files(), t.truncated() ? "+" : "", Formatter.size(t.bytes()));
    }
}
