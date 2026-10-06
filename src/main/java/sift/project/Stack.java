// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Sri Ganty

package sift.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Names the framework and test framework a project declares.
 *
 * <p>Both answers come from dependency declarations in the build files, never
 * from scanning source. A framework that is not declared there is "unknown",
 * which is more useful than a confident wrong answer.
 */
public final class Stack {

    private Stack() {}

    public static Optional<String> framework(Project project) {
        Path root = project.root();
        return switch (project.buildSystem()) {
            case MAVEN, GRADLE -> firstMatch(text(root, "pom.xml", "build.gradle", "build.gradle.kts"),
                    List.of("spring-boot", "Spring Boot", "quarkus", "Quarkus", "micronaut", "Micronaut"));
            case NPM -> {
                // Order matters: Next.js apps also depend on react, and the
                // more specific framework is the more informative answer.
                String[][] known = {{"next", "Next.js"}, {"nuxt", "Nuxt"}, {"@angular/core", "Angular"},
                        {"svelte", "Svelte"}, {"vue", "Vue"}, {"react", "React"}, {"express", "Express"}};
                for (String[] k : known) {
                    if (Node.declares(root, k[0])) {
                        yield Optional.of(k[1]);
                    }
                }
                yield Optional.empty();
            }
            default -> project.language() == Project.Language.PYTHON
                    ? wordMatch(pythonDeclarations(root), List.of("django", "Django", "fastapi", "FastAPI", "flask", "Flask"))
                    : Optional.empty();
        };
    }

    public static Optional<String> testFramework(Project project) {
        Path root = project.root();
        return switch (project.buildSystem()) {
            case MAVEN, GRADLE -> {
                String t = text(root, "pom.xml", "build.gradle", "build.gradle.kts");
                if (t.contains("junit-jupiter") || t.contains("useJUnitPlatform")) {
                    yield Optional.of("JUnit 5");
                }
                if (t.toLowerCase(Locale.ROOT).contains("testng")) {
                    yield Optional.of("TestNG");
                }
                yield t.contains("<artifactId>junit</artifactId>") || t.contains("junit:junit")
                        ? Optional.of("JUnit 4") : Optional.empty();
            }
            case NPM -> {
                for (String name : List.of("vitest", "jest", "mocha")) {
                    if (Node.declares(root, name)) {
                        yield Optional.of(name);
                    }
                }
                yield Optional.empty();
            }
            default -> project.language() == Project.Language.PYTHON
                    ? wordMatch(pythonDeclarations(root) + text(root, "pytest.ini", "tox.ini"), List.of("pytest", "pytest"))
                    : Optional.empty();
        };
    }

    private static Optional<String> firstMatch(String text, List<String> needleAndLabel) {
        for (int i = 0; i < needleAndLabel.size(); i += 2) {
            if (text.contains(needleAndLabel.get(i))) {
                return Optional.of(needleAndLabel.get(i + 1));
            }
        }
        return Optional.empty();
    }

    // Word boundaries keep "flask" from matching inside an unrelated longer
    // name; hyphenated siblings such as "django-filter" still imply Django.
    private static Optional<String> wordMatch(String text, List<String> needleAndLabel) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (int i = 0; i < needleAndLabel.size(); i += 2) {
            if (Pattern.compile("\\b" + Pattern.quote(needleAndLabel.get(i)) + "\\b").matcher(lower).find()) {
                return Optional.of(needleAndLabel.get(i + 1));
            }
        }
        return Optional.empty();
    }

    private static String pythonDeclarations(Path root) {
        return text(root, "pyproject.toml", "requirements.txt", "requirements-dev.txt", "setup.py");
    }

    private static String text(Path root, String... files) {
        StringBuilder sb = new StringBuilder();
        for (String f : files) {
            try {
                Path p = root.resolve(f);
                if (Files.isRegularFile(p)) {
                    sb.append(Files.readString(p)).append('\n');
                }
            } catch (IOException e) {
                // Unreadable or not UTF-8: treat as absent rather than guess.
            }
        }
        return sb.toString();
    }
}
