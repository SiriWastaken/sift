# SIFT

SIFT is a small command-line tool that answers four questions about the
project you are standing in:

| Command        | Question                                  |
|----------------|-------------------------------------------|
| `sift inspect` | What am I looking at?                     |
| `sift check`   | Does this project currently look healthy? |
| `sift doctor`  | What might be wrong with it?              |
| `sift diff`    | What did I change?                        |

It runs locally, needs no network and no accounts, and prints only what it can
back with evidence. Anything it cannot identify is reported as `unknown`
instead of guessed.

## Why

Opening an unfamiliar repository means running `git status`, finding the build
tool, guessing the test command, and checking whether the Java or lockfile
setup is sane. SIFT does that routine in one place, using each ecosystem's own
conventions, and never invents a command a project did not ask for.

## Install

Requires Java 17+ and Maven to build, and `git` on the `PATH` for anything
that touches the repository.

```sh
mvn package
bin/sift --help          # bin\sift.cmd on Windows
```

To use it as `sift` anywhere, link the launcher into a directory on your
`PATH`:

```sh
ln -s "$PWD/bin/sift" ~/.local/bin/sift
```

## Usage

```text
sift inspect                      Describe the project in the current directory
sift check                        Build and test it; report whether it looks healthy
sift doctor                       Look for configuration and repository problems
sift diff [--staged] [<revision>] Summarize uncommitted changes
sift --help | --version
```

Exit codes: `0` success, `1` a problem was found or a check failed, `2` invalid
usage.

### inspect

```text
$ sift inspect
SIFT

project    sift
language   Java
framework  unknown
build      Maven
tests      JUnit 5
files      38 (161.3 kB)
git        main
status     clean
```

Framework and test framework come from declared dependencies only. A walk of
the directory skips `.git`, `node_modules`, `target`, `build`, `dist`,
`.venv` and similar, never follows symlinks, and stops at 100,000 files.

### check

```text
$ sift check
SIFT CHECK

✓ Git working tree
✓ Build
✓ Tests (100 passed)

PROJECT HEALTHY
```

SIFT runs only commands it knows are right for the detected project, and shows
the command and trimmed output when one fails:

```text
✗ Tests
  10 passed, 2 failed

  RuleTest.rejectsEmpty:84 expected: <true>

  ran: mvn -B test

PROJECT NEEDS ATTENTION
```

| Project | Steps                                                                |
|---------|----------------------------------------------------------------------|
| Maven   | `mvn test-compile`, `mvn test` (uses `mvnw` if present)              |
| Gradle  | `classes testClasses`, `test` (just `build` for non-Java layouts)    |
| Node    | the `typecheck`, `build`, `lint` and `test` scripts that exist, via the package manager named by `packageManager` or the lockfile |
| Python  | `pytest` and `ruff`, only if configured and installed                |

A failed build skips the tests. Each step has a 10 minute timeout. Commands are
never read from project files and never go through a shell.

### doctor

Prints findings only, each with the evidence behind it:

```text
✗ Build configuration
  pom.xml targets Java 21, but the current runtime reports Java 17
  Expected: Java 21
  Found:    Java 17

! Git state
  diverged from upstream: 15 ahead, 1 behind
```

Checks include unresolved conflicts and conflict markers (with `file:line`),
a merge or rebase in progress, detached HEAD, tracked build output and
credential-looking files, tracked files over 5 MB, a missing README or
`.gitignore`, a malformed `pom.xml`, a Java version newer than the runtime,
missing or conflicting Node lockfiles, and `node_modules` not installed.

### diff

```text
$ sift diff
SIFT DIFF

working tree vs HEAD

1 file changed, 1 untracked

  M  +1  -0  src/main/java/sift/Main.java
  ?  new     notes.tmp

Summary

  1 source file modified
  1 untracked file (line counts not included)
  1 line added, 0 removed
  note: source changed but no test file did
```

Git supplies every count. SIFT adds the untracked files `git diff` leaves out
and groups changes by file role, judged from the path alone. `sift diff main`
compares the working tree with another revision; `sift diff --staged` shows
the index.

## Supported projects

Detection reads marker files in the current directory only. If several are
present, the first row wins.

| Marker                                        | Language                       | Build system |
|-----------------------------------------------|--------------------------------|--------------|
| `pom.xml`                                     | Java                           | Maven        |
| `build.gradle[.kts]`, `settings.gradle[.kts]` | Java if `src/main/java` exists | Gradle       |
| `package.json` (+ `tsconfig.json`)            | JavaScript (TypeScript)        | npm          |
| `pyproject.toml`                              | Python                         | pyproject    |
| `setup.py`, `requirements.txt`                | Python                         | unknown      |

## Development

```sh
mvn verify      # compile, run tests, build target/sift.jar
```

Tests build throwaway git repositories, so they need `git` on the `PATH`.
CI runs on Linux, macOS and Windows with Java 17 and 21. See
[CONTRIBUTING.md](CONTRIBUTING.md).

## License

GPL-3.0-or-later. You may use, study, modify and redistribute SIFT, but any
distributed derivative must stay under the same license with its source
available. See [LICENSE](LICENSE).
