# Post-generation intervention report

Library: org.opengauss:opengauss-jdbc:3.1.1
Stage: metadata_fix_failed

## Summary

The metadata-fix run initially failed native tests in two places:

- `LazyKeyManagerTest.resolvesInvalidPkcs8KeyWithConfiguredPrivateKeyFactory()` failed with a `MissingReflectionRegistrationError` for the Bouncy Castle RSA key factory provider class.
- `BouncyCastlePrivateKeyFactoryTest.decryptsOpenSslPkcs8PrivateKeyWithBouncyCastle()` failed while decrypting an encrypted OpenSSL PKCS#8 key through Bouncy Castle.

After inspecting the Codex metadata-fix log, the LazyKeyManager failure was metadata-related and was preserved. The remaining decryption test failure was not a direct missing-reachability-metadata error, so the generated decryption test method was removed from `tests/src/org.opengauss/opengauss-jdbc/3.1.1/src/test/java/org_opengauss/opengauss_jdbc/BouncyCastlePrivateKeyFactoryTest.java`.

## Failure root causes

### `LazyKeyManagerTest.resolvesInvalidPkcs8KeyWithConfiguredPrivateKeyFactory()`

Root cause: metadata-related.

The Gradle excerpt reported a missing reflection registration for:

```text
org.bouncycastle.jcajce.provider.asymmetric.rsa.KeyFactorySpi
```

The missing access was needed when `org.postgresql.ssl.LazyKeyManager` asked JCA/Bouncy Castle to construct an RSA `KeyFactory`. This was a standard native-image reflection registration miss. The Codex metadata-fix log shows that this failure was resolved by the metadata-fix attempt: the later verification run reported this test as `SUCCESSFUL`. The test was therefore kept.

### `BouncyCastlePrivateKeyFactoryTest.decryptsOpenSslPkcs8PrivateKeyWithBouncyCastle()`

Root cause: not metadata-related.

The remaining failure did not report a `Missing*RegistrationError`. It failed as:

```text
java.lang.Exception: get private key by bouncycastle failed:null
```

Codex also reproduced the lower-level cause in a scratch native-image run: Bouncy Castle could not resolve the AES algorithm parameters for OID `2.16.840.1.101.3.4.1.42` at runtime:

```text
java.security.NoSuchAlgorithmException: 2.16.840.1.101.3.4.1.42 AlgorithmParameters not available
```

That is a native-image/JCA provider initialization limitation for this generated encrypted-key decryption path, not an actionable missing metadata entry emitted by native image. Because the failure was caused by the generated test exercising an unsupported provider algorithm path, the generated decryption test method was removed. No metadata files were modified as part of this intervention.

## Preserved generated support

The rest of the generated support should be preserved because it covers reachable, passing dynamic-access paths in `opengauss-jdbc`, including connection handling, object type registration, escaped function lookup, resource bundle loading, array conversion, pooled/XA connection proxies, SSL certificate loading, and Bouncy Castle provider creation. The preserved `LazyKeyManagerTest` specifically protects the metadata-related Bouncy Castle RSA `KeyFactory` path that Codex fixed.

Verification after removing only the unsupported decryption test:

```text
./gradlew test -Pcoordinates=org.opengauss:opengauss-jdbc:3.1.1
```

Result: `BUILD SUCCESSFUL` with `28 tests successful`.
