# Instructions when developing tests

## Code Style
- Tests should throw and assert against standard Java exceptions; do not use `GradleException` in test code.
- The model may change `buildArgs` in `build.gradle` only in `graalvmNative`, and only to add a flag from the
  allowed list in section 2.7.1 of [the test contract](../../docs/functional-spec/test-contract.md), in its narrowest
  form, when the failure shows the library needs it. Flags in section 2.7.2 are never added.
