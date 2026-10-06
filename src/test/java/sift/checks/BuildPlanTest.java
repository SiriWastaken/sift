// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Sri Ganty

package sift.checks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static sift.TestRepo.write;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import sift.checks.BuildPlan.Step;
import sift.project.ProjectDetector;

class BuildPlanTest {

    @TempDir Path dir;

    private List<String> names() {
        return BuildPlan.of(ProjectDetector.detect(dir)).stream().map(Step::name).toList();
    }

    private String commandOf(String step) {
        return BuildPlan.of(ProjectDetector.detect(dir)).stream()
                .filter(s -> s.name().equals(step)).findFirst().orElseThrow()
                .command().stream().collect(java.util.stream.Collectors.joining(" "));
    }

    @Test void unknownProjectRunsNothing() {
        assertEquals(List.of(), names());
    }

    @Test void mavenBuildsThenTests() throws Exception {
        write(dir, "pom.xml", "<project/>");
        assertEquals(List.of("Build", "Tests"), names());
        // No mvnw in the project, so the global tool is used.
        assertEquals(true, commandOf("Tests").endsWith("mvn -B test"));
    }

    @Test void nodeRunsOnlyScriptsThatExist() throws Exception {
        write(dir, "package.json", "{\"scripts\": {\"build\": \"tsc\", \"test\": \"vitest\"}}");
        assertEquals(List.of("Build", "Tests"), names());
    }

    @Test void nodePlaceholderTestScriptIsNotRun() throws Exception {
        write(dir, "package.json",
                "{\"scripts\": {\"test\": \"echo \\\"Error: no test specified\\\" && exit 1\"}}");
        assertEquals(List.of(), names());
    }

    @Test void nodeUsesTheProjectsPackageManager() throws Exception {
        write(dir, "package.json", "{\"scripts\": {\"test\": \"vitest\"}}");
        write(dir, "pnpm-lock.yaml", "");
        assertEquals(true, commandOf("Tests").endsWith("pnpm run test"));
    }

    @Test void packageManagerFieldBeatsLockfile() throws Exception {
        write(dir, "package.json", "{\"packageManager\": \"yarn@4.0.0\", \"scripts\": {\"test\": \"x\"}}");
        write(dir, "package-lock.json", "{}");
        assertEquals(true, commandOf("Tests").endsWith("yarn run test"));
    }

    @Test void pythonTestsOnlyWhenTestsExist() throws Exception {
        write(dir, "pyproject.toml", "[project]\nname='x'\n");
        assertEquals(List.of(), names());
        write(dir, "tests/test_a.py", "");
        assertEquals(List.of("Tests"), names());
    }

    @Test void gradleWithoutJavaLayoutOnlyBuilds() throws Exception {
        write(dir, "build.gradle.kts", "");
        assertEquals(List.of("Build"), names());
        write(dir, "src/main/java/A.java", "");
        assertEquals(List.of("Build", "Tests"), names());
    }

    @Test void summaries() {
        assertEquals("12 passed", BuildPlan.mavenSummary(
                "Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1 s - in A\n"
              + "Tests run: 12, Failures: 0, Errors: 0, Skipped: 0\n"));
        assertEquals("9 passed, 3 failed", BuildPlan.mavenSummary("Tests run: 12, Failures: 2, Errors: 1\n"));
        assertEquals("1 passed, 2 failed", BuildPlan.pytestSummary("2 failed, 1 passed in 0.1s"));
        assertEquals("4 passed", BuildPlan.pytestSummary("4 passed in 0.02s"));
        assertEquals("3 passed, 2 failed", BuildPlan.gradleSummary("5 tests completed, 2 failed"));
        assertNull(BuildPlan.mavenSummary("compilation error"));
    }
}
