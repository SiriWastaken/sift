// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Sri Ganty

package sift;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import java.util.Arrays;

import sift.cli.CheckCommand;
import sift.cli.DiffCommand;
import sift.cli.InspectCommand;

/** Entry point: parses the command line and maps it to an exit code. */
public final class Main {

    /** Process exit codes. Kept deliberately few. */
    public static final int OK = 0;
    public static final int PROBLEM = 1;
    public static final int USAGE = 2;

    private static final String HELP = """
            SIFT - see what matters in a software project.

            Usage: sift <command>

            Commands:
              inspect    Describe the project in the current directory
              check      Build and test it; report whether it looks healthy
              diff       Summarize uncommitted changes
                         (sift diff [--staged] [<revision>])

            Options:
              --help     Show this help
              --version  Show the version
            """;

    private Main() {}

    public static void main(String[] args) {
        // The output uses ✓ and ✗; do not leave their encoding to the platform default.
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), false, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8);
        int code = run(args, Path.of("").toAbsolutePath(), out, err);
        out.flush();
        System.exit(code);
    }

    /**
     * Runs SIFT. Streams and working directory are parameters so tests can
     * drive the whole CLI without spawning a JVM or touching global state.
     */
    public static int run(String[] args, Path cwd, PrintStream out, PrintStream err) {
        if (args.length == 0) {
            out.print(HELP);
            return OK;
        }
        switch (args[0]) {
            case "--help", "-h", "help" -> {
                out.print(HELP);
                return OK;
            }
            case "--version", "-V" -> {
                out.println("sift " + version());
                return OK;
            }
            case "inspect" -> {
                return InspectCommand.run(cwd, out);
            }
            case "check" -> {
                return CheckCommand.run(cwd, out);
            }
            case "diff" -> {
                return DiffCommand.run(Arrays.asList(args).subList(1, args.length), cwd, out, err);
            }
            default -> {
                err.println("Unknown command: " + args[0]);
                err.println();
                err.println("Run 'sift --help' for available commands.");
                return USAGE;
            }
        }
    }

    // Read from the jar manifest (see pom.xml). Null when running from loose
    // class files, e.g. inside an IDE, where "dev" is an honest answer.
    private static String version() {
        String v = Main.class.getPackage().getImplementationVersion();
        return v != null ? v : "dev";
    }
}
