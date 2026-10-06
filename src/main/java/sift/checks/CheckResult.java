// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Sri Ganty

package sift.checks;

/**
 * @param name   short noun phrase, e.g. "Tests"
 * @param detail evidence shown under the name, one fact per line; may be empty
 */
public record CheckResult(String name, Status status, String detail) {

    public static CheckResult pass(String name) { return new CheckResult(name, Status.PASS, ""); }
    public static CheckResult warn(String name, String detail) { return new CheckResult(name, Status.WARN, detail); }
    public static CheckResult fail(String name, String detail) { return new CheckResult(name, Status.FAIL, detail); }
    public static CheckResult skip(String name, String detail) { return new CheckResult(name, Status.SKIP, detail); }
}
