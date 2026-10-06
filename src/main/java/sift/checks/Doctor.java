// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Sri Ganty

package sift.checks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import sift.exec.Exec;
import sift.git.GitRepository;
import sift.project.Node;
import sift.project.Project;
import sift.project.Project.BuildSystem;
import sift.project.Project.Language;

/**
 * Diagnoses a project with deterministic, evidence-backed checks.
 *
 * <p>A check reports a finding only when it can name the file, line, count or
 * version that proves it. Anything that would need guessing about intent is
 * left out. Checks that do not apply to the project return {@link Status#SKIP}.
 */
public final class Doctor {

    /** Tracked files larger than this are almost always accidents (archives, dumps, binaries). */
    private static final long LARGE_FILE_BYTES = 5L * 1024 * 1024;
    private static final int EVIDENCE_LINES = 5;

    private Doctor() {}

    public static List<CheckResult> run(Project project) {
        Path root = project.root();
        List<CheckResult> results = new ArrayList<>();

        Optional<GitRepository.State> git = GitRepository.read(root);
        if (git.isEmpty()) {
            results.add(CheckResult.warn("Git repository", "not inside a git repository (or git is not installed)"));
        } else {
            // One listing serves every check that looks at tracked files.
            List<String> tracked = GitRepository.run(root, "ls-files", "-z")
                    .map(o -> Arrays.stream(o.split("\0")).filter(s -> !s.isEmpty()).toList())
                    .orElse(List.of());
            results.add(gitState(root, git.get()));
            results.add(conflictMarkers(root));
            results.add(trackedBuildOutput(project, tracked));
            results.add(trackedCredentials(tracked));
            results.add(largeFiles(root, tracked));
        }
        results.add(expectedFiles(root));

        switch (project.buildSystem()) {
            case MAVEN -> {
                results.add(mavenPom(root));
                results.add(testsExist(root));
            }
            case GRADLE -> {
                results.add(gradleWrapper(root));
                if (project.language() == Language.JAVA) {
                    results.add(testsExist(root));
                }
            }
            case NPM -> results.addAll(node(root));
            case PYTHON_PACKAGING, UNKNOWN -> {
                if (project.language() == Language.PYTHON) {
                    results.add(pythonTests(root));
                }
            }
        }
        results.removeIf(r -> r.status() == Status.SKIP);
        return results;
    }

    // ---- Git ----------------------------------------------------------------

    private static CheckResult gitState(Path root, GitRepository.State s) {
        List<String> problems = new ArrayList<>();
        if (s.conflicts() > 0) {
            return CheckResult.fail("Git state", s.conflicts() + " file(s) have unresolved merge conflicts");
        }
        String operation = operationInProgress(root);
        if (operation != null) {
            problems.add(operation + " in progress");
        }
        if (s.branch().equals("(detached)")) {
            problems.add("HEAD is detached; commits made now belong to no branch");
        }
        if (s.behind() > 0) {
            problems.add(s.behind() + " commit(s) behind upstream");
        }
        if (s.changed() > 0 || s.untracked() > 0) {
            problems.add(s.describe());
        }
        return problems.isEmpty() ? CheckResult.pass("Git state")
                : CheckResult.warn("Git state", String.join("\n", problems));
    }

    // Git records an unfinished operation as marker files inside the git dir.
    // rev-parse --git-path resolves the right directory for worktrees too.
    private static String operationInProgress(Path root) {
        String[][] markers = {{"MERGE_HEAD", "merge"}, {"CHERRY_PICK_HEAD", "cherry-pick"},
                {"REVERT_HEAD", "revert"}, {"rebase-merge", "rebase"}, {"rebase-apply", "rebase"}};
        for (String[] m : markers) {
            Optional<String> path = GitRepository.run(root, "rev-parse", "--git-path", m[0]);
            if (path.isPresent() && Files.exists(root.resolve(path.get().trim()))) {
                return m[1];
            }
        }
        return null;
    }

    private static CheckResult conflictMarkers(Path root) {
        // git grep searches tracked files only, honours .gitattributes binary
        // detection (-I) and is far faster than walking the tree in Java.
        // Seven characters plus a space is how git writes the markers.
        Optional<String> hits = GitRepository.run(root, "grep", "-n", "-I", "-E", "-e", "^<{7} ", "-e", "^>{7} ");
        if (hits.isEmpty() || hits.get().isBlank()) {
            return CheckResult.pass("Conflict markers");
        }
        return CheckResult.fail("Conflict markers", "unresolved markers at:\n" + evidence(hits.get().lines()
                .map(l -> l.substring(0, l.indexOf(':', l.indexOf(':') + 1))).toList()));
    }

    private static CheckResult trackedBuildOutput(Project project, List<String> tracked) {
        List<String> dirs = new ArrayList<>(List.of("node_modules", ".venv", "__pycache__"));
        // "target" and "build" are ordinary source directory names in other
        // ecosystems, so they only count as build output where the build tool says so.
        if (project.buildSystem() == BuildSystem.MAVEN) {
            dirs.add("target");
        }
        if (project.buildSystem() == BuildSystem.GRADLE) {
            dirs.add("build");
        }
        List<String> found = new ArrayList<>();
        for (String dir : dirs) {
            long n = tracked.stream().filter(p -> p.startsWith(dir + "/") || p.contains("/" + dir + "/")).count();
            if (n > 0) {
                found.add(n + " tracked file(s) under " + dir + "/");
            }
        }
        return found.isEmpty() ? CheckResult.pass("Tracked build output")
                : CheckResult.warn("Tracked build output", String.join("\n", found) + "\nadd to .gitignore and run: git rm -r --cached <dir>");
    }

    private static CheckResult trackedCredentials(List<String> tracked) {
        // Names only: SIFT never reads file contents looking for secrets.
        List<String> hits = tracked.stream().filter(p -> {
            String name = p.substring(p.lastIndexOf('/') + 1).toLowerCase();
            boolean env = (name.equals(".env") || name.startsWith(".env."))
                    && !name.endsWith(".example") && !name.endsWith(".sample") && !name.endsWith(".template");
            return env || name.endsWith(".pem") || name.endsWith(".p12") || name.endsWith(".pfx")
                    || name.equals("id_rsa") || name.equals("id_ed25519");
        }).toList();
        return hits.isEmpty() ? CheckResult.pass("Tracked credentials")
                : CheckResult.warn("Tracked credentials", "committed files that usually hold secrets:\n" + evidence(hits));
    }

    private static CheckResult largeFiles(Path root, List<String> tracked) {
        List<String> big = new ArrayList<>();
        for (String p : tracked) {
            try {
                long size = Files.size(root.resolve(p));
                if (size > LARGE_FILE_BYTES) {
                    big.add(String.format("%s (%.1f MB)", p, size / 1048576.0));
                }
            } catch (IOException e) {
                // Tracked but deleted in the working tree; `sift diff` reports that.
            }
        }
        return big.isEmpty() ? CheckResult.pass("Large files")
                : CheckResult.warn("Large files", "tracked files over 5 MB:\n" + evidence(big));
    }

    // ---- Project layout -----------------------------------------------------

    private static CheckResult expectedFiles(Path root) {
        boolean readme;
        try (Stream<Path> files = Files.list(root)) {
            readme = files.anyMatch(f -> f.getFileName().toString().toLowerCase().startsWith("readme"));
        } catch (IOException e) {
            readme = true; // unreadable directory: do not claim something is missing
        }
        List<String> missing = new ArrayList<>();
        if (!readme) {
            missing.add("README");
        }
        if (!Files.exists(root.resolve(".gitignore"))) {
            missing.add(".gitignore");
        }
        return missing.isEmpty() ? CheckResult.pass("Expected files")
                : CheckResult.warn("Expected files", "missing: " + String.join(", ", missing));
    }

    private static CheckResult testsExist(Path root) {
        return Files.isDirectory(root.resolve("src/test/java")) ? CheckResult.pass("Tests present")
                : CheckResult.warn("Tests present", "no src/test/java directory");
    }

    private static CheckResult pythonTests(Path root) {
        boolean has = Files.isDirectory(root.resolve("tests")) || Files.isDirectory(root.resolve("test"))
                || Files.isRegularFile(root.resolve("pytest.ini")) || Files.isRegularFile(root.resolve("conftest.py"));
        return has ? CheckResult.pass("Tests present") : CheckResult.warn("Tests present", "no tests/ directory or pytest configuration");
    }

    // ---- Maven --------------------------------------------------------------

    private static CheckResult mavenPom(Path root) {
        Document pom;
        try {
            pom = parseXml(root.resolve("pom.xml"));
        } catch (Exception e) {
            return CheckResult.fail("Build configuration", "pom.xml is not well-formed XML:\n" + firstLine(e.getMessage()));
        }
        Integer required = requiredJava(pom);
        if (required == null) {
            return CheckResult.pass("Build configuration");
        }
        int found = runtimeJava(root);
        if (found > 0 && found < required) {
            return CheckResult.fail("Build configuration",
                    "pom.xml targets Java " + required + ", but the current runtime reports Java " + found
                            + "\nExpected: Java " + required + "\nFound:    Java " + found);
        }
        return CheckResult.pass("Build configuration");
    }

    private static Document parseXml(Path file) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        // A pom is untrusted input in a freshly cloned repository: no DTDs, no
        // external entities, nothing that could read files or reach the network.
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var builder = f.newDocumentBuilder();
        builder.setErrorHandler(null); // the default handler prints to stderr
        return builder.parse(file.toFile());
    }

    /** The Java release the pom asks for, or null if it does not say (or says it indirectly). */
    private static Integer requiredJava(Document pom) {
        // Most specific first: the compiler plugin's <release>, then the properties it reads.
        for (String tag : List.of("release", "maven.compiler.release", "maven.compiler.source", "java.version")) {
            NodeList nodes = pom.getElementsByTagName(tag);
            for (int i = 0; i < nodes.getLength(); i++) {
                Integer v = javaVersion(nodes.item(i).getTextContent().trim());
                if (v != null) {
                    return v;
                }
            }
        }
        return null;
    }

    // "21" -> 21, "1.8" -> 8. Property references like ${x} return null: SIFT does not resolve them.
    static Integer javaVersion(String text) {
        Matcher m = Pattern.compile("^(?:1\\.)?(\\d+)$").matcher(text);
        return m.find() ? Integer.valueOf(m.group(1)) : null;
    }

    /** Feature version of the {@code java} Maven would use, or 0 if it cannot be determined. */
    private static int runtimeJava(Path root) {
        // Maven honours JAVA_HOME before PATH, so SIFT does too. Asking the
        // actual executable matters: the JVM running SIFT may not be the one
        // the build uses.
        String home = System.getenv("JAVA_HOME");
        String java = home != null && !home.isBlank()
                ? Path.of(home, "bin", "java").toString() : "java";
        Exec.Result r = Exec.combined(root, Duration.ofSeconds(10), List.of(java, "-version"));
        Matcher m = Pattern.compile("version \"(?:1\\.)?(\\d+)").matcher(r.output());
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    // ---- Gradle -------------------------------------------------------------

    private static CheckResult gradleWrapper(Path root) {
        boolean has = Files.exists(root.resolve("gradlew")) || Files.exists(root.resolve("gradlew.bat"));
        return has ? CheckResult.pass("Gradle wrapper")
                : CheckResult.warn("Gradle wrapper", "no gradlew; contributors need a matching Gradle installed");
    }

    // ---- Node ---------------------------------------------------------------

    private static List<CheckResult> node(Path root) {
        List<CheckResult> out = new ArrayList<>();
        boolean deps = Node.hasDependencies(root);
        List<String> locks = Node.lockfiles(root);

        if (deps && locks.isEmpty()) {
            out.add(CheckResult.warn("Lockfile", "package.json declares dependencies but no lockfile exists;\ninstalls are not reproducible"));
        } else if (locks.size() > 1) {
            out.add(CheckResult.warn("Lockfile", "several lockfiles: " + String.join(", ", locks)
                    + "\nthey will drift apart; keep the one your package manager writes"));
        } else {
            out.add(CheckResult.pass("Lockfile"));
        }

        boolean installed = Files.isDirectory(root.resolve("node_modules")) || Files.exists(root.resolve(".pnp.cjs"));
        if (deps && !installed) {
            out.add(CheckResult.warn("Dependencies", "node_modules is missing; run: " + Node.packageManager(root) + " install"));
        } else {
            out.add(CheckResult.pass("Dependencies"));
        }

        String test = Node.scriptBody(root, "test");
        if (test == null || test.contains("no test specified")) {
            out.add(CheckResult.warn("Tests present", "package.json has no working \"test\" script"));
        } else {
            out.add(CheckResult.pass("Tests present"));
        }
        return out;
    }

    // ---- helpers ------------------------------------------------------------

    /** First few items, one per line, with a count for the rest. */
    private static String evidence(List<String> items) {
        String shown = items.stream().limit(EVIDENCE_LINES).map(s -> "  " + s).collect(Collectors.joining("\n"));
        return items.size() > EVIDENCE_LINES ? shown + "\n  ... and " + (items.size() - EVIDENCE_LINES) + " more" : shown;
    }

    private static String firstLine(String s) {
        return s == null ? "" : s.lines().findFirst().orElse("");
    }
}
