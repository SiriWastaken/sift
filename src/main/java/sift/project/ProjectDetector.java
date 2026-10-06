// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Sri Ganty

package sift.project;

import java.nio.file.Files;
import java.nio.file.Path;

import sift.project.Project.BuildSystem;
import sift.project.Project.Language;

/**
 * Identifies a project from marker files in its root directory.
 *
 * <p>Detection is intentionally shallow and conservative: only well-known
 * files in the directory itself are consulted, and anything not recognised is
 * reported as unknown. A monorepo with markers for several ecosystems gets the
 * first match in the order below, which puts the strictest markers first.
 */
public final class ProjectDetector {

    private ProjectDetector() {}

    public static Project detect(Path dir) {
        Path root = dir.toAbsolutePath().normalize();
        // getFileName() is null for a filesystem root such as "/" or "C:\".
        String name = root.getFileName() != null ? root.getFileName().toString() : root.toString();

        if (has(root, "pom.xml")) {
            return new Project(name, root, Language.JAVA, BuildSystem.MAVEN);
        }
        if (has(root, "build.gradle.kts", "build.gradle", "settings.gradle.kts", "settings.gradle")) {
            // Gradle also builds Kotlin and Groovy; only call it Java when the
            // conventional Java source layout is present.
            boolean java = Files.isDirectory(root.resolve("src/main/java"));
            return new Project(name, root, java ? Language.JAVA : Language.UNKNOWN, BuildSystem.GRADLE);
        }
        if (has(root, "package.json")) {
            // A tsconfig is the reliable signal that the code is TypeScript.
            Language lang = has(root, "tsconfig.json") ? Language.TYPESCRIPT : Language.JAVASCRIPT;
            return new Project(name, root, lang, BuildSystem.NPM);
        }
        if (has(root, "pyproject.toml")) {
            return new Project(name, root, Language.PYTHON, BuildSystem.PYTHON_PACKAGING);
        }
        // Older Python layouts have no single build-system marker, so the
        // language is known but the build system is not.
        if (has(root, "setup.py", "requirements.txt")) {
            return new Project(name, root, Language.PYTHON, BuildSystem.UNKNOWN);
        }
        return new Project(name, root, Language.UNKNOWN, BuildSystem.UNKNOWN);
    }

    private static boolean has(Path root, String... files) {
        for (String f : files) {
            if (Files.isRegularFile(root.resolve(f))) {
                return true;
            }
        }
        return false;
    }
}
