# Oracle PKI tests

Tests use wallet, secret-store, JCA keystore, trust-manager, and command APIs.
The source artifact contains only a dummy source file, so API signatures and
implementation paths were checked against the library bytecode.

## Uncovered command entry points

`oracle.security.pki.OracleSecretStoreTextUI.main` and
`oracle.security.pki.textui.OraclePKITextUI.main` call `System.exit` on every
normal path. They expose no non-exiting command API in this artifact. Calling
them would terminate the test runner. A constructor-only test would not assert
command behavior, and launching an external JVM would not verify the same
behavior inside Native Image, so these entry points remain uncovered.

## LDAP socket scope

The LDAP socket test initializes the factory from a password-protected wallet
and verifies public socket-port validation. It does not attempt a handshake:
the socket customization hardcodes obsolete cipher suites, and the default
wallet-free initialization looks up `SSLContext.init(Object, Object, Object)`
rather than the actual typed signature. These unsupported paths are not asserted
as expected failures.
