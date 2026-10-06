# Contributing

Bug reports, fixes and focused features are welcome.

## Before you start

SIFT is deliberately small. A new command or check should answer a real
question, report only what it can prove, and be explainable in a sentence.
Open an issue first if you are unsure it fits.

## Workflow

- Run `mvn verify`; it must pass.
- Add tests with the change. Tests that need git build a temporary repository
  (see `src/test/java/sift/TestRepo.java`).
- Keep commits small, with a message that says what changed and why.
- Match the surrounding code. Comments explain why, not what.

## License

SIFT is GPL-3.0-or-later. By contributing you agree that your work is released
under the same license. Each Java file starts with an SPDX header; keep it on
new files.
