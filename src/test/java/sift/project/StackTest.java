// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Sri Ganty

package sift.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static sift.TestRepo.write;

import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StackTest {

    @TempDir Path dir;

    private Project project() { return ProjectDetector.detect(dir); }

    @Test void unknownWhenNothingIsDeclared() throws Exception {
        write(dir, "package.json", "{\"dependencies\": {\"left-pad\": \"1\"}}");
        assertEquals(Optional.empty(), Stack.framework(project()));
        assertEquals(Optional.empty(), Stack.testFramework(project()));
    }

    @Test void nextWinsOverReact() throws Exception {
        write(dir, "package.json", "{\"dependencies\": {\"react\": \"18\", \"next\": \"14\"}}");
        assertEquals(Optional.of("Next.js"), Stack.framework(project()));
    }

    @Test void mavenSpringBootAndJUnit5() throws Exception {
        write(dir, "pom.xml", "<project><parent><artifactId>spring-boot-starter-parent</artifactId></parent>"
                + "<dependency><artifactId>junit-jupiter</artifactId></dependency></project>");
        assertEquals(Optional.of("Spring Boot"), Stack.framework(project()));
        assertEquals(Optional.of("JUnit 5"), Stack.testFramework(project()));
    }

    @Test void junit4IsNotMistakenForJUnit5() throws Exception {
        write(dir, "pom.xml", "<project><dependency><artifactId>junit</artifactId></dependency></project>");
        assertEquals(Optional.of("JUnit 4"), Stack.testFramework(project()));
    }

    @Test void gradleUsesJUnitPlatform() throws Exception {
        write(dir, "build.gradle.kts", "tasks.test { useJUnitPlatform() }");
        assertEquals(Optional.of("JUnit 5"), Stack.testFramework(project()));
    }

    @Test void pythonFrameworkAndPytest() throws Exception {
        write(dir, "pyproject.toml", "[project]\ndependencies = [\"Django>=5\", \"pytest\"]\n");
        assertEquals(Optional.of("Django"), Stack.framework(project()));
        assertEquals(Optional.of("pytest"), Stack.testFramework(project()));
    }
}
