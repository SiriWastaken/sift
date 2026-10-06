// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Sri Ganty

package sift.checks;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import sift.project.Node;
import sift.project.Project;
import sift.project.Project.Language;

/**
 * Decides which commands are a legitimate way to build and test a project.
 *
 * <p>Commands come only from SIFT's own knowledge of each ecosystem's
 * conventions (Maven's {@code test}, Gradle's {@code classes}, the scripts a
 * {@code package.json} defines, ...). A command is never lifted from a project
 * file and handed to a shell, so opening a hostile repository and running
 * {@code sift check} does no more than running its build tool would.
 */
public final class BuildPlan {

    private static final boolean WINDOWS = System.getProperty("os.name").startsWith("Windows");

    /**
     * One command to run.
     *
     * @param probe   if non-null, run first; when it fails the step is skipped with {@code missing}
     * @param summary turns the command's output into a short result like "12 passed"; may return null
     */
    public record Step(String name, List<String> command, List<String> probe, String missing,
                       Function<String, String> summary) {
        Step(String name, List<String> command) {
            this(name, command, null, null, out -> null);
        }
    }

    private BuildPlan() {}

    /** The steps for {@code project}, in the order they should run. Empty when nothing is known. */
    public static List<Step> of(Project project) {
        Path root = project.root();
        List<Step> steps = new ArrayList<>();
        switch (project.buildSystem()) {
            case MAVEN -> {
                // -B: no download-progress noise; -q on the build so only errors print.
                String mvn = wrapper(root, "mvnw");
                steps.add(new Step("Build", tool(mvn, "-B", "-q", "test-compile")));
                steps.add(new Step("Tests", tool(mvn, "-B", "test"), null, null, BuildPlan::mavenSummary));
            }
            case GRADLE -> {
                String gradle = wrapper(root, "gradlew");
                if (project.language() == Language.JAVA) {
                    steps.add(new Step("Build", tool(gradle, "--console=plain", "-q", "classes", "testClasses")));
                    steps.add(new Step("Tests", tool(gradle, "--console=plain", "test"),
                            null, null, BuildPlan::gradleSummary));
                } else {
                    // Not a Java layout, so SIFT cannot know the task names;
                    // `build` is the one task every Gradle project defines.
                    steps.add(new Step("Build", tool(gradle, "--console=plain", "build")));
                }
            }
            case NPM -> addNode(root, steps);
            case PYTHON_PACKAGING -> addPython(root, steps);
            case UNKNOWN -> {
                if (project.language() == Language.PYTHON) {
                    addPython(root, steps);
                }
            }
        }
        return steps;
    }

    private static void addNode(Path root, List<Step> steps) {
        String pm = Node.packageManager(root);
        List<String> scripts = Node.scripts(root);
        // Only scripts the project itself defines are run, so a missing
        // "lint" is skipped rather than invented.
        for (String[] s : new String[][] {
                {"typecheck", "Type check"}, {"build", "Build"}, {"lint", "Lint"}, {"test", "Tests"}}) {
            if (!scripts.contains(s[0])) {
                continue;
            }
            // `npm init` leaves a placeholder test script that always fails.
            if (s[0].equals("test") && Node.scriptBody(root, "test").contains("no test specified")) {
                continue;
            }
            steps.add(new Step(s[1], tool(pm, "run", s[0])));
        }
    }

    private static void addPython(Path root, List<Step> steps) {
        String python = python(root);
        boolean pytestConfigured = Files.isDirectory(root.resolve("tests")) || Files.isDirectory(root.resolve("test"))
                || Files.isRegularFile(root.resolve("pytest.ini")) || Files.isRegularFile(root.resolve("conftest.py"))
                || fileContains(root.resolve("pyproject.toml"), "[tool.pytest");
        if (pytestConfigured) {
            steps.add(new Step("Tests", tool(python, "-m", "pytest", "-q"),
                    tool(python, "-m", "pytest", "--version"), "pytest is not installed",
                    BuildPlan::pytestSummary));
        }
        boolean ruffConfigured = Files.isRegularFile(root.resolve("ruff.toml"))
                || Files.isRegularFile(root.resolve(".ruff.toml"))
                || fileContains(root.resolve("pyproject.toml"), "[tool.ruff");
        if (ruffConfigured) {
            steps.add(new Step("Lint", tool(python, "-m", "ruff", "check", "."),
                    tool(python, "-m", "ruff", "--version"), "ruff is not installed", out -> null));
        }
    }

    /** The project's own virtualenv if it has one, otherwise the system interpreter. */
    private static String python(Path root) {
        Path venv = WINDOWS ? root.resolve(".venv/Scripts/python.exe") : root.resolve(".venv/bin/python");
        if (Files.isRegularFile(venv)) {
            return venv.toString();
        }
        // The Windows installer provides "python"; "python3" is the portable name elsewhere.
        return WINDOWS ? "python" : "python3";
    }

    /** The project's wrapper script (mvnw, gradlew) if present, else the globally installed tool. */
    private static String wrapper(Path root, String name) {
        Path script = root.resolve(WINDOWS ? name + ".cmd" : name);
        if (Files.isRegularFile(script) && (WINDOWS || Files.isExecutable(script))) {
            return script.toString();
        }
        return name.equals("mvnw") ? "mvn" : "gradle";
    }

    /**
     * On Windows, npm/pnpm/yarn/mvn/gradle are .cmd scripts that CreateProcess
     * cannot start by bare name, so they go through cmd.exe. The arguments are
     * all fixed strings chosen by SIFT, so nothing is re-parsed unsafely.
     */
    private static List<String> tool(String exe, String... args) {
        List<String> cmd = new ArrayList<>();
        if (WINDOWS) {
            cmd.add("cmd");
            cmd.add("/c");
        }
        cmd.add(exe);
        cmd.addAll(List.of(args));
        return cmd;
    }

    private static boolean fileContains(Path file, String text) {
        try {
            return Files.isRegularFile(file) && Files.readString(file).contains(text);
        } catch (java.io.IOException e) {
            return false;
        }
    }

    // Surefire prints one "Tests run:" line per class and a final total; the last one is the total.
    private static final Pattern SUREFIRE =
            Pattern.compile("Tests run: (\\d+), Failures: (\\d+), Errors: (\\d+)(?:, Skipped: (\\d+))?");

    static String mavenSummary(String output) {
        Matcher m = SUREFIRE.matcher(output);
        int[] t = null;
        while (m.find()) {
            t = new int[] {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)) + Integer.parseInt(m.group(3))};
        }
        return t == null ? null : counts(t[0] - t[1], t[1]);
    }

    // Gradle is quiet on success; it only states totals when something failed.
    private static final Pattern GRADLE = Pattern.compile("(\\d+) tests completed, (\\d+) failed");

    static String gradleSummary(String output) {
        Matcher m = GRADLE.matcher(output);
        if (!m.find()) {
            return null;
        }
        int total = Integer.parseInt(m.group(1));
        int failed = Integer.parseInt(m.group(2));
        return counts(total - failed, failed);
    }

    static String pytestSummary(String output) {
        int passed = lastCount(output, "passed");
        int failed = lastCount(output, "failed") + lastCount(output, "errors?");
        return passed + failed == 0 ? null : counts(passed, failed);
    }

    private static int lastCount(String output, String word) {
        Matcher m = Pattern.compile("(\\d+) " + word + "\\b").matcher(output);
        int n = 0;
        while (m.find()) {
            n = Integer.parseInt(m.group(1));
        }
        return n;
    }

    private static String counts(int passed, int failed) {
        return failed == 0 ? passed + " passed" : passed + " passed, " + failed + " failed";
    }
}
