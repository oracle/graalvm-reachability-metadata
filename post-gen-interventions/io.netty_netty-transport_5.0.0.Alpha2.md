# Post-generation intervention

Library: io.netty:netty-transport:5.0.0.Alpha2
Stage: `metadata_fix_failed`

## Failure summary

Two native-image tests fail while Netty's `ReflectiveChannelFactory` uses
`Class.newInstance()` to create `NioServerSocketChannel` and `NioDatagramChannel`.
Both fail with `NoSuchMethodException` for the zero-argument constructor. This is
an incomplete reflection-registration problem, not an unsupported native-image
feature or a test defect.

The generated test-only metadata registers the HTTP codecs, handlers, and the
Datagram handler, but it does not register the constructors of
`io.netty.channel.socket.nio.NioServerSocketChannel` or
`io.netty.channel.socket.nio.NioDatagramChannel` for reflective construction.
The Codex log requested at
`logs/io.netty:netty-transport:5.0.0.Alpha2/metadata-fix/codex.log` is not
present in this worktree, so it provides no further explanation of why Codex
stopped before adding those registrations.

## Intervention

No generated tests or support files were removed. The failing paths are valid
consumer-like TCP and UDP Netty exercises, and the passing NIO event-loop test
remains independent coverage. The generated support should be preserved because
it exercises the reflective channel-factory path that the missing metadata must
cover; deleting it would hide the metadata gap rather than establish support.
