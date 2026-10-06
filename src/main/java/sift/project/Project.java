// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Sri Ganty

package sift.project;

import java.nio.file.Path;

/**
 * What SIFT could confidently determine about a directory.
 *
 * @param language    {@link Language#UNKNOWN} rather than a guess
 * @param buildSystem {@link BuildSystem#UNKNOWN} rather than a guess
 */
public record Project(String name, Path root, Language language, BuildSystem buildSystem) {

    public enum Language {
        JAVA("Java"), TYPESCRIPT("TypeScript"), JAVASCRIPT("JavaScript"), PYTHON("Python"), UNKNOWN("unknown");

        private final String label;

        Language(String label) { this.label = label; }

        public String label() { return label; }
    }

    public enum BuildSystem {
        MAVEN("Maven"), GRADLE("Gradle"), NPM("npm"), PYTHON_PACKAGING("pyproject"), UNKNOWN("unknown");

        private final String label;

        BuildSystem(String label) { this.label = label; }

        public String label() { return label; }
    }
}
