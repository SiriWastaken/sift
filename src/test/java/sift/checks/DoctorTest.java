// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Sri Ganty

package sift.checks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static sift.TestRepo.commitAll;
import static sift.TestRepo.git;
import static sift.TestRepo.init;
import static sift.TestRepo.write;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import sift.project.ProjectDetector;

class DoctorTest {

    @TempDir Path dir;

    private List<CheckResult> run() {
        return Doctor.run(ProjectDetector.detect(dir));
    }

    private CheckResult find(List<CheckResult> all, String name) {
        return all.stream().filter(r -> r.name().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError(name + " not in " + all));
    }

    private static String pom(String javaVersion) {
        return "<project><modelVersion>4.0.0</modelVersion><properties><maven.compiler.release>"
                + javaVersion + "</maven.compiler.release></properties></project>";
    }

    @Test void cleanRepositoryHasNoFindings() throws Exception {
        init(dir);
        write(dir, "README.md", "# x");
        write(dir, ".gitignore", "target/");
        commitAll(dir, "init");
        assertTrue(run().stream().allMatch(r -> r.status() == Status.PASS), run().toString());
    }

    @Test void notARepositoryIsReported() {
        assertEquals(Status.WARN, find(run(), "Git repository").status());
    }

    @Test void conflictMarkersAreNamedWithFileAndLine() throws Exception {
        init(dir);
        write(dir, "a.txt", "ok\n<<<<<<< HEAD\nmine\n=======\ntheirs\n>>>>>>> branch\n");
        commitAll(dir, "init");
        CheckResult r = find(run(), "Conflict markers");
        assertEquals(Status.FAIL, r.status());
        assertTrue(r.detail().contains("a.txt:2"), r.detail());
    }

    @Test void trackedBuildOutputAndCredentials() throws Exception {
        init(dir);
        write(dir, "node_modules/lib/index.js", "x");
        write(dir, ".env", "TOKEN=1");
        write(dir, ".env.example", "TOKEN=");
        commitAll(dir, "init");
        List<CheckResult> all = run();
        assertEquals(Status.WARN, find(all, "Tracked build output").status());
        CheckResult creds = find(all, "Tracked credentials");
        assertEquals(Status.WARN, creds.status());
        assertTrue(creds.detail().contains(".env"));
        assertTrue(!creds.detail().contains(".env.example"));
    }

    @Test void mavenTargetDirectoryOnlyCountsForMaven() throws Exception {
        init(dir);
        write(dir, "target/Foo.class", "x");
        commitAll(dir, "init");
        assertEquals(Status.PASS, find(run(), "Tracked build output").status());

        write(dir, "pom.xml", pom("17"));
        commitAll(dir, "pom");
        assertEquals(Status.WARN, find(run(), "Tracked build output").status());
    }

    @Test void javaVersionTooNewForRuntimeFails() throws Exception {
        write(dir, "pom.xml", pom("99"));
        CheckResult r = find(run(), "Build configuration");
        assertEquals(Status.FAIL, r.status());
        assertTrue(r.detail().contains("Expected: Java 99"), r.detail());
        assertTrue(r.detail().contains("Found:"), r.detail());
    }

    @Test void olderJavaTargetIsFine() throws Exception {
        write(dir, "pom.xml", pom("8"));
        assertEquals(Status.PASS, find(run(), "Build configuration").status());
    }

    @Test void malformedPomFails() throws Exception {
        write(dir, "pom.xml", "<project><unclosed></project>");
        CheckResult r = find(run(), "Build configuration");
        assertEquals(Status.FAIL, r.status());
        assertTrue(r.detail().contains("not well-formed"));
    }

    @Test void pomWithDoctypeIsRejectedNotExpanded() throws Exception {
        write(dir, "pom.xml", "<!DOCTYPE project [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><project>&x;</project>");
        assertEquals(Status.FAIL, find(run(), "Build configuration").status());
    }

    @Test void javaVersionParsing() {
        assertEquals(21, Doctor.javaVersion("21"));
        assertEquals(8, Doctor.javaVersion("1.8"));
        assertEquals(null, Doctor.javaVersion("${java.version}"));
    }

    @Test void nodeWithoutLockfileOrInstall() throws Exception {
        write(dir, "package.json", "{\"dependencies\": {\"left-pad\": \"1.0.0\"}}");
        List<CheckResult> all = run();
        assertEquals(Status.WARN, find(all, "Lockfile").status());
        assertEquals(Status.WARN, find(all, "Dependencies").status());
        assertEquals(Status.WARN, find(all, "Tests present").status());
    }

    @Test void nodeWithConflictingLockfiles() throws Exception {
        write(dir, "package.json", "{}");
        write(dir, "yarn.lock", "");
        write(dir, "package-lock.json", "{}");
        CheckResult r = find(run(), "Lockfile");
        assertEquals(Status.WARN, r.status());
        assertTrue(r.detail().contains("yarn.lock") && r.detail().contains("package-lock.json"));
    }

    @Test void nodeWithRealTestScriptPasses() throws Exception {
        write(dir, "package.json", "{\"scripts\": {\"test\": \"vitest\"}}");
        assertEquals(Status.PASS, find(run(), "Tests present").status());
    }

    @Test void unresolvedConflictInIndexFails() throws Exception {
        init(dir);
        write(dir, "a.txt", "base\n");
        commitAll(dir, "base");
        git(dir, "checkout", "-q", "-b", "other");
        write(dir, "a.txt", "other\n");
        commitAll(dir, "other");
        git(dir, "checkout", "-q", "main");
        write(dir, "a.txt", "main\n");
        commitAll(dir, "main");
        new ProcessBuilder("git", "merge", "other").directory(dir.toFile())
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor();

        CheckResult r = find(run(), "Git state");
        assertEquals(Status.FAIL, r.status());
        assertTrue(r.detail().contains("1 file"), r.detail());
    }

    @Test void upstreamDivergenceAndLagAreReported() throws Exception {
        Path remote = dir.resolve("remote.git");
        Path mine = dir.resolve("mine");
        Path other = dir.resolve("other");
        java.nio.file.Files.createDirectories(remote);
        git(remote, "init", "-q", "--bare", "-b", "main");

        java.nio.file.Files.createDirectories(mine);
        init(mine);
        write(mine, "a.txt", "1\n");
        commitAll(mine, "first");
        git(mine, "remote", "add", "origin", remote.toString());
        git(mine, "push", "-q", "-u", "origin", "main");

        git(dir, "clone", "-q", remote.toString(), "other");
        git(other, "config", "user.email", "t@example.com");
        git(other, "config", "user.name", "T");
        write(other, "b.txt", "2\n");
        commitAll(other, "second");
        git(other, "push", "-q", "origin", "main");
        git(mine, "fetch", "-q");

        String behind = Doctor.run(ProjectDetector.detect(mine)).stream()
                .filter(r -> r.name().equals("Git state")).findFirst().orElseThrow().detail();
        assertTrue(behind.contains("1 commit behind upstream"), behind);

        write(mine, "c.txt", "3\n");
        commitAll(mine, "local only");
        String diverged = Doctor.run(ProjectDetector.detect(mine)).stream()
                .filter(r -> r.name().equals("Git state")).findFirst().orElseThrow().detail();
        assertTrue(diverged.contains("diverged from upstream: 1 ahead, 1 behind"), diverged);
    }
}
