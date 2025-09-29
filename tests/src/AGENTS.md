# Instructions when developing tests

## Code Style
- Tests should throw and assert against standard Java exceptions; do not use `GradleException` in test code.
- The model may modify `buildArgs` in `build.gradle` only in `graalvmNative` and only to add `--add-opens` or `--add-exports arguments`.