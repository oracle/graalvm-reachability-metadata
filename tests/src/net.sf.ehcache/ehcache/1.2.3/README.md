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
