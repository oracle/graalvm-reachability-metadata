# TLS dynamic-access tests

- `UtilTest` makes a real HTTPS request with explicit cipher suites and TLS
  versions, exercising the array intersections used to configure the socket.
- `RouteExceptionTest` exhausts two TLS configurations against a TLS-1.3-only
  loopback server. It asserts that the final handshake exception preserves the
  earlier failure as a suppressed exception.
- `PlatformTest` verifies a certificate-pinned HTTPS request, which requires
  extracting the trust manager from the client's socket factory. On the JVM,
  this library operation requires
  `--add-opens=java.base/sun.security.ssl=ALL-UNNAMED`: without it, the pinned
  request throws `InaccessibleObjectException` when OkHttp reads
  `SSLSocketFactoryImpl.context`. The opening also permits its subsequent read
  of `SSLContextImpl.trustManager`; no other JDK package is opened.

- `PlatformInnerJdkWithJettyBootPlatformTest` makes an HTTPS request with
  Jetty's ALPN API present and both HTTP/2 and HTTP/1.1 enabled. When the TLS
  connection negotiates no application protocol, it verifies the HTTP/1.1
  response and checks through a network interceptor that OkHttp has released
  Jetty's per-socket provider registration after the handshake. The API
  dependency selects OkHttp's Jetty platform without boot-classpath changes.

`TlsTestSupport` uses a test-only PKCS12 keystore containing a self-signed
`localhost` certificate with a `127.0.0.1` subject alternative name. Its password
is `changeit`; it contains no production credentials.

The other reported classes have no supported public-API route on this test JVM:

- `Platform$Android` and `OptionalMethod` require Android's platform SSL socket
  implementation and traffic-statistics APIs.
- `AndroidTrustRootIndex` is selected only by the Android platform and requires
  Android's trust-manager implementation.
- `Platform$JettyNegoProvider` callbacks require Jetty's ALPN boot integration
  for old JDKs. The ALPN API dependency exercises provider registration and
  cleanup, but does not install protocol-negotiation callbacks in the JDK TLS
  implementation.

These paths are left uncovered rather than introducing Android shadow classes,
calling internal APIs, or selecting a different test JVM.
