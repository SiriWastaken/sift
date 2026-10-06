# SIFT

SIFT is a small command-line tool that tells you what you are looking at when
you `cd` into a software project: the language, the build system, and the state
of the Git working tree. It runs locally, needs no network, and prints only
what it can determine with confidence.

> **Status:** early. Only `inspect` exists. `check`, `doctor` and `diff` are
> planned.

## Install

Requires Java 17+ and Maven to build.

```sh
mvn package
java -jar target/sift-0.1.0.jar --help
```

Put a one-line wrapper on your `PATH` to run it as `sift`, e.g.
`alias sift='java -jar /path/to/sift/target/sift-0.1.0.jar'`.

## Usage

```text
sift inspect     Describe the project in the current directory
sift --help
sift --version
```

```text
$ sift inspect
SIFT

project   sift
language  Java
build     Maven
git       main
status    clean
```

Anything SIFT cannot identify is reported as `unknown` instead of guessed.

Exit codes: `0` success, `1` a problem was detected (reserved for upcoming
commands), `2` invalid usage.

## Supported projects

Detection looks only at marker files in the current directory.

| Marker                                   | Language              | Build system |
|------------------------------------------|-----------------------|--------------|
| `pom.xml`                                | Java                  | Maven        |
| `build.gradle[.kts]`, `settings.gradle[.kts]` | Java if `src/main/java` exists | Gradle |
| `package.json` (+ `tsconfig.json`)       | JavaScript (TypeScript) | npm        |
| `pyproject.toml`                         | Python                | pyproject    |
| `setup.py`, `requirements.txt`           | Python                | unknown      |

If several markers are present, the first row above wins.

## Development

```sh
mvn verify     # compile, run tests, build the jar
```

Git state is read through the `git` executable, so tests that touch it need
`git` on the `PATH`.

## License

GPL-3.0-or-later. You may use, study, modify and redistribute SIFT, but any
distributed derivative must stay under the same license with its source
available. See [LICENSE](LICENSE).
