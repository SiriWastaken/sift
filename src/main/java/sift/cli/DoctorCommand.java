// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Sri Ganty

package sift.cli;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

import sift.Main;
import sift.checks.CheckResult;
import sift.checks.Doctor;
import sift.checks.Status;
import sift.output.Formatter;
import sift.project.ProjectDetector;

/** {@code sift doctor}: answers "what might be wrong with this project?". */
public final class DoctorCommand {

    private DoctorCommand() {}

    public static int run(Path dir, PrintStream out) {
        List<CheckResult> results = Doctor.run(ProjectDetector.detect(dir));

        out.print("SIFT DOCTOR\n\n");
        // Findings only: a wall of passing checks would bury the one that matters.
        results.stream()
                .filter(r -> r.status() != Status.PASS)
                .sorted(java.util.Comparator.comparing(CheckResult::status).reversed()) // FAIL before WARN
                .forEach(r -> out.print(Formatter.result(r)));

        long fail = results.stream().filter(r -> r.status() == Status.FAIL).count();
        long warn = results.stream().filter(r -> r.status() == Status.WARN).count();
        long pass = results.size() - fail - warn;
        if (fail + warn == 0) {
            out.println("✓ No problems found (" + results.size() + " checks)");
        } else {
            out.println(pass + " passed, " + fail + (fail == 1 ? " problem, " : " problems, ")
                    + warn + (warn == 1 ? " warning" : " warnings"));
        }
        return fail > 0 ? Main.PROBLEM : Main.OK;
    }
}
