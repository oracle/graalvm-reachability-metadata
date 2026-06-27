# Post-generation intervention report

Library: org.bouncycastle:bc-fips:2.1.2
Stage: metadata_fix_failed

## Summary

The native test run failed with `87` failures for the generated `bc-fips` support. The failures fell into two groups:

- Non-metadata-generated test problems were removed from the test sources.
- Metadata-related failures remain and should not be removed, because they identify real missing native-image reachability support for `bc-fips` serialization paths.

The Codex metadata-fix log ends while a follow-up `./gradlew test -Pcoordinates=org.bouncycastle:bc-fips:2.1.2 --stacktrace` command is still in progress, after Codex had identified missing provider/security setup and missing reflective construction metadata. It did not complete a verified metadata fix.

## Non-metadata failures removed

The following generated tests were removed because their root causes were not missing reachability metadata:

- Tests that failed with `java.lang.SecurityException: Attempted to verify a provider that was not registered at build time` for `org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider`. This is native-image security-provider registration/configuration behavior, not a missing metadata entry in the library metadata.
- Tests that tried to call generated assumptions about private `writeObject`/`readObject` serialization hooks and failed with `NoSuchMethodException`/`NoSuchMethodError`. These are generated test bugs: those hook methods are not present for the targeted key classes.
- Tests that asserted `NamedParameterSpec` support for EC initialization and failed with `InvalidParameterSpecException` or `InvalidAlgorithmParameterException`. That is unsupported provider behavior for the tested API path, not metadata.
- Tests that asserted implementation-specific serialized stream contents/class descriptors and failed with assertion errors. These are brittle generated-test assumptions, not metadata gaps.

Removed generated test methods were in:

- `BaseSecretKeyFactoryTest`
- `ClassUtilAnonymous1Test`
- `ClassUtilTest`
- `Prov15EdDSAPublicKeyTest`
- `ProvDHPublicKeyTest`
- `ProvDSAPrivateKeyTest`
- `ProvDSTU4145PublicKeyTest`
- `ProvECAnonymous55Test`
- `ProvECPrivateKeyTest`
- `ProvECPublicKeyTest`
- `ProvGOST3410PrivateKeyTest`
- `ProvGOST3410PublicKeyTest`
- `ProvLMSPrivateKeyTest`
- `ProvLMSPublicKeyTest`
- `ProvRSAPrivateKeyTest`
- `ProvSecretKeySpecTest`

## Metadata-related failures preserved

The remaining failures are metadata-related and should stay as generated coverage:

- Multiple serialization round-trip tests fail with `ClassNotFoundException` for `org.bouncycastle.jcajce.provider.Prov*` key classes, including XDH, EC, EdDSA, GOST, LMS, RSA, DSA, and related public/private key implementation classes. These failures indicate that the native image does not have the required serialization/class metadata for deserializing those provider implementation classes.
- Several serialization tests fail with `MissingReflectionRegistrationError: Cannot reflectively invoke constructor 'public java.lang.Object()'`. Codex identified this as missing reflective constructor registration used by serialization hook paths, but the metadata fix did not complete and verify successfully.
- The excerpted `ProvXDHPrivateKeyTest` and `ProvXDHPublicKeyTest` failures are in this metadata-related group: `ObjectInputStream` cannot resolve `org.bouncycastle.jcajce.provider.ProvXDHPrivateKey` or `ProvXDHPublicKey` in the native executable.

Codex could not resolve these because the metadata-fix session stopped before a completed verify run. The log shows it was still trying to add narrowly scoped registrations for `BouncyCastleFipsProvider()` and `java.lang.Object()` through test-local native-image metadata, after earlier attempts changed failure modes but did not produce a passing native run.

## Why the remaining generated support should be preserved

The preserved tests exercise real `bc-fips` native-image behavior around object serialization/deserialization of provider key implementations. Those paths reveal missing reachability metadata rather than unsupported API usage or invalid generated assertions. Keeping them preserves useful coverage for future metadata work: the next metadata fix can add the missing serialization/reflection registrations and verify the same tests instead of losing coverage for `bc-fips` key serialization support.

## Verification performed

After removing the non-metadata generated test methods, test sources still compile:

```text
./gradlew compileTestJava -Pcoordinates=org.bouncycastle:bc-fips:2.1.2 --stacktrace
```
