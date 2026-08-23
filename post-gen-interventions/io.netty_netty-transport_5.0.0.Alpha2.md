# Post-generation intervention

Library: io.netty:netty-transport:5.0.0.Alpha2
Stage: `metadata_fix_failed`

## Summary

`nativeTest` failed in two generated tests: `NettyTests.servesHttpResponse()` and
`NioDatagramChannelConfigTest.readsAndWritesMulticastTimeToLive()`. Both fail in
`ReflectiveChannelFactory` because `Class.newInstance()` cannot find the
no-argument constructors of `NioServerSocketChannel` and `NioDatagramChannel`.
`NioEventLoopTest.runsTaskOnNioEventLoop()` passed.

## Root cause and required metadata follow-up

This is a metadata failure, not an unsupported native-image behavior. The
`index.json` entry for `5.0.0.Alpha2` selects metadata version `5.0.0.Alpha2`,
but no corresponding `metadata/io.netty/netty-transport/5.0.0.Alpha2/`
directory exists. The missing bundle must register the no-argument
constructors used by `ReflectiveChannelFactory` for:

- `io.netty.channel.socket.nio.NioServerSocketChannel`
- `io.netty.channel.socket.nio.NioDatagramChannel`

Codex could not collect or apply the repair: its pinned Gradle reproduction was
rejected twice because the managed Codex execution environment does not support
command approvals (`approval request failed`). It therefore never ran the
metadata collection or a confirming test.

## Intervention

No generated tests or support files were removed, and no metadata files were
modified. The HTTP server/client and datagram tests exercise valid Netty public
transport behavior and expose missing reflection registrations; their generated
handlers, certificates, and test-only metadata remain necessary support. The
passing event-loop test should also remain as independent coverage of the NIO
event-loop path.
