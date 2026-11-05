# Test scope

The suite exercises cache operations, eviction, XML configuration, persistence,
serialization through library APIs, blocking/loading decorators, asynchronous
commands, and loopback RMI/JNDI replication and bootstrap.

Automatic-discovery tests cover provider configuration, explicit peer
notifications, cache-name filtering, and heartbeat-based peer expiry. They do
not start multicast networking: outgoing multicast is denied on the generation
host (`SocketException: Operation not permitted`). `PayloadUtilTest` exercises
the pipe-delimited URL format through the provider factory, not heartbeat
compression. Multicast packet exchange and the compression helpers remain
untested; no test skips or tolerates failures under Native Image.

Servlet tests cover configured exception logging with and without stack traces,
response capture, gzip negotiation, cached-page replay, HTTP empty-body rules,
and application-shutdown cleanup. Servlet fixtures implement the dependency's
interfaces in the test namespace without reflection or added dependencies.

`BeanHandler.createChild`'s creator-method invocation is not reachable through
`ConfigurationFactory`: its root is always a `Configuration`, and the supported
nested configuration beans expose `add...` methods, not `create...` methods.
Existing `BeanHandlerTest` covers those supported nested configuration paths.
`PayloadUtil` is package-private and its nonconstant methods are called only
by multicast heartbeat threads. The provider-factory delimiter test does not
initialize it; reaching its logger initialization requires multicast packet
processing, which is unavailable on this host as noted above.
