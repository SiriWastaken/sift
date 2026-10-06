// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Sri Ganty

package sift.project;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import sift.project.Project.BuildSystem;
import sift.project.Project.Language;

class ProjectDetectorTest {

    @TempDir Path dir;

    private void touch(String... files) throws IOException {
        for (String f : files) {
            Path p = dir.resolve(f);
            Files.createDirectories(p.getParent());
            Files.createFile(p);
        }
    }

    private void expect(Language lang, BuildSystem build) {
        Project p = ProjectDetector.detect(dir);
        assertEquals(lang, p.language());
        assertEquals(build, p.buildSystem());
    }

    @Test void maven() throws IOException {
        touch("pom.xml");
        expect(Language.JAVA, BuildSystem.MAVEN);
    }

    @Test void gradleWithJavaSources() throws IOException {
        touch("build.gradle", "src/main/java/A.java");
        expect(Language.JAVA, BuildSystem.GRADLE);
    }

    @Test void gradleKotlinDslWithoutJavaSourcesDoesNotGuessLanguage() throws IOException {
        touch("build.gradle.kts");
        expect(Language.UNKNOWN, BuildSystem.GRADLE);
    }

    @Test void typescript() throws IOException {
        touch("package.json", "tsconfig.json");
        expect(Language.TYPESCRIPT, BuildSystem.NPM);
    }

    @Test void javascript() throws IOException {
        touch("package.json");
        expect(Language.JAVASCRIPT, BuildSystem.NPM);
    }

    @Test void pythonPyproject() throws IOException {
        touch("pyproject.toml");
        expect(Language.PYTHON, BuildSystem.PYTHON_PACKAGING);
    }

    @Test void pythonRequirementsOnly() throws IOException {
        touch("requirements.txt");
        expect(Language.PYTHON, BuildSystem.UNKNOWN);
    }

    @Test void unknown() {
        expect(Language.UNKNOWN, BuildSystem.UNKNOWN);
    }

    @Test void ambiguousProjectUsesDocumentedPrecedence() throws IOException {
        touch("pom.xml", "package.json");
        expect(Language.JAVA, BuildSystem.MAVEN);
    }

    @Test void nameIsDirectoryName() {
        assertEquals(dir.getFileName().toString(), ProjectDetector.detect(dir).name());
    }
}
