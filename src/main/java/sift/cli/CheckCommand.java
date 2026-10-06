// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Sri Ganty

package sift.cli;

import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import sift.Main;
import sift.checks.BuildPlan;
import sift.checks.BuildPlan.Step;
import sift.checks.CheckResult;
import sift.checks.Status;
import sift.exec.Exec;
import sift.git.GitRepository;
import sift.output.Formatter;
import sift.project.Project;
import sift.project.ProjectDetector;

/**
 * {@code sift check}: answers "does this project currently look healthy?".
 *
 * <p>Runs the project's own build and test tooling (chosen by
 * {@link BuildPlan}) and reports one line per step. Output of a step is shown
 * only when it fails, since then it is the evidence.
 */
public final class CheckCommand {

    // Generous: a cold Gradle or npm build is slow, a hung one should still end.
    private static final Duration STEP_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(30);
    private static final int EVIDENCE_LINES = 12;

    private CheckCommand() {}

    public static int run(Path dir, PrintStream out) {
        Project project = ProjectDetector.detect(dir);
        List<CheckResult> results = new ArrayList<>();
        out.print("SIFT CHECK\n\n");

        emit(results, out, workingTree(dir));

        List<Step> plan = BuildPlan.of(project);
        if (plan.isEmpty()) {
            emit(results, out, CheckResult.skip("Build and tests", "no build or test command recognised for this project"));
        }
        boolean buildFailed = false;
        for (Step step : plan) {
            // Tests cannot say anything useful about code that does not compile.
            CheckResult r = buildFailed && step.name().equals("Tests")
                    ? CheckResult.skip(step.name(), "skipped: build failed")
                    : execute(project.root(), step);
            buildFailed |= step.name().equals("Build") && r.status() == Status.FAIL;
            emit(results, out, r);
        }

        long failed = results.stream().filter(r -> r.status() == Status.FAIL).count();
        long warned = results.stream().filter(r -> r.status() == Status.WARN).count();
        if (failed > 0) {
            out.println("PROJECT NEEDS ATTENTION");
            return Main.PROBLEM;
        }
        out.println(warned > 0 ? "PROJECT HEALTHY (" + Formatter.count((int) warned, "warning") + ")" : "PROJECT HEALTHY");
        return Main.OK;
    }

    // Steps can take minutes, so each line is printed (and flushed) as it finishes.
    private static void emit(List<CheckResult> results, PrintStream out, CheckResult r) {
        results.add(r);
        out.print(Formatter.result(r));
        out.flush();
    }

    private static CheckResult workingTree(Path dir) {
        Optional<GitRepository.State> git = GitRepository.read(dir);
        if (git.isEmpty()) {
            return CheckResult.skip("Git working tree", "not a git repository");
        }
        var s = git.get();
        if (s.clean()) {
            return CheckResult.pass("Git working tree");
        }
        // Uncommitted work is normal during development, so it is a warning, not a failure.
        return CheckResult.warn("Git working tree", s.describe());
    }

    private static CheckResult execute(Path root, Step step) {
        if (step.probe() != null && !Exec.combined(root, PROBE_TIMEOUT, step.probe()).ok()) {
            return CheckResult.skip(step.name(), step.missing());
        }
        Exec.Result r = Exec.combined(root, STEP_TIMEOUT, step.command());
        String ran = "ran: " + String.join(" ", step.command());
        return switch (r.outcome()) {
            case NOT_FOUND -> CheckResult.fail(step.name(), step.command().get(0) + " was not found\n" + ran);
            case TIMED_OUT -> CheckResult.fail(step.name(), "timed out after " + STEP_TIMEOUT.toMinutes() + " minutes\n" + ran);
            case EXITED -> {
                String summary = step.summary().apply(r.output());
                if (r.exit() == 0) {
                    yield CheckResult.pass(summary == null ? step.name() : step.name() + " (" + summary + ")");
                }
                String head = summary == null ? "exit code " + r.exit() : summary;
                yield CheckResult.fail(step.name(), head + "\n\n" + evidence(r.output()) + "\n\n" + ran);
            }
        };
    }

    /**
     * The part of a failing command's output worth reading. Maven marks its
     * diagnostics with "[ERROR]"; for anything else the tail is where
     * tools print what went wrong.
     */
    static String evidence(String output) {
        List<String> lines = output.lines().filter(l -> !l.isBlank()).toList();
        List<String> errors = lines.stream()
                .filter(l -> l.startsWith("[ERROR]"))
                .filter(l -> !l.contains("[Help") && !l.contains("stack trace") && !l.contains("Re-run Maven")
                        && !l.contains("For more information") && !l.contains("-> ["))
                .map(l -> l.substring("[ERROR]".length()).strip())
                .filter(l -> !l.isEmpty())
                .toList();
        List<String> shown = !errors.isEmpty()
                ? errors.subList(0, Math.min(errors.size(), EVIDENCE_LINES))
                : lines.subList(Math.max(0, lines.size() - EVIDENCE_LINES), lines.size());
        return String.join("\n", shown);
    }
}
