// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Sri Ganty

package sift.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Facts about a Node project read from {@code package.json} and its lockfiles.
 *
 * <p>There is no JSON parser in the JDK and SIFT takes no dependencies, so the
 * few fields needed are matched with regular expressions. That is sound only
 * because the questions are narrow: top-level key names inside the
 * {@code "scripts"} object, and whether a section is non-empty. Anything
 * subtler would justify a real parser.
 */
public final class Node {

    /** Lockfile name to the package manager that writes it, in priority order. */
    private static final Map<String, String> LOCKFILES = new LinkedHashMap<>();
    static {
        LOCKFILES.put("pnpm-lock.yaml", "pnpm");
        LOCKFILES.put("yarn.lock", "yarn");
        LOCKFILES.put("package-lock.json", "npm");
        LOCKFILES.put("npm-shrinkwrap.json", "npm");
        LOCKFILES.put("bun.lockb", "bun");
        LOCKFILES.put("bun.lock", "bun");
    }

    private static final Pattern SCRIPTS = Pattern.compile("\"scripts\"\\s*:\\s*\\{([^}]*)}");
    private static final Pattern SCRIPT_NAME = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"");
    private static final Pattern PACKAGE_MANAGER = Pattern.compile("\"packageManager\"\\s*:\\s*\"([a-z]+)@");
    private static final Pattern DEPENDENCIES =
            Pattern.compile("\"(?:dependencies|devDependencies)\"\\s*:\\s*\\{\\s*\"");

    private Node() {}

    /** Names of lockfiles present in {@code root}, in priority order. */
    public static java.util.List<String> lockfiles(Path root) {
        return LOCKFILES.keySet().stream().filter(f -> Files.isRegularFile(root.resolve(f))).toList();
    }

    /**
     * The package manager the project has clearly chosen: the
     * {@code packageManager} field wins, then a lockfile. Falls back to npm,
     * which ships with Node, when nothing says otherwise.
     */
    public static String packageManager(Path root) {
        Matcher m = PACKAGE_MANAGER.matcher(read(root));
        if (m.find()) {
            return m.group(1);
        }
        var locks = lockfiles(root);
        return locks.isEmpty() ? "npm" : LOCKFILES.get(locks.get(0));
    }

    /** Names of the entries in {@code "scripts"}, in file order. */
    public static java.util.List<String> scripts(Path root) {
        Matcher block = SCRIPTS.matcher(read(root));
        java.util.List<String> names = new java.util.ArrayList<>();
        if (block.find()) {
            Matcher name = SCRIPT_NAME.matcher(block.group(1));
            while (name.find()) {
                names.add(name.group(1));
            }
        }
        return names;
    }

    /** The script's command line, or null if it is not defined. */
    public static String scriptBody(Path root, String script) {
        Matcher block = SCRIPTS.matcher(read(root));
        if (!block.find()) {
            return null;
        }
        Matcher m = Pattern.compile("\"" + Pattern.quote(script) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                .matcher(block.group(1));
        return m.find() ? m.group(1) : null;
    }

    /** True if package.json declares at least one dependency or devDependency. */
    public static boolean hasDependencies(Path root) {
        return DEPENDENCIES.matcher(read(root)).find();
    }

    /** True if package.json mentions {@code name} as a key, e.g. a dependency called "next". */
    public static boolean declares(Path root, String name) {
        return Pattern.compile("\"" + Pattern.quote(name) + "\"\\s*:").matcher(read(root)).find();
    }

    private static String read(Path root) {
        try {
            return Files.readString(root.resolve("package.json"));
        } catch (IOException e) {
            return "";
        }
    }
}
