// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Sri Ganty

package sift.checks;

/** Outcome of one check. SKIP means "not applicable here", never "passed". */
public enum Status { PASS, WARN, FAIL, SKIP }
