The previous feature test did not pass.
Pick a different uncovered `{library}` feature and write tests for it.
Keep existing tests unchanged.

Issue-Requested Metadata:
{issue_requested_metadata_context}

The tests must execute under native image. Do not skip, disable, or short-circuit test logic in native image using assumptions, `@DisabledInNativeImage`, `isNativeImageRuntime()`, `ImageInfo.inImageRuntimeCode()`, or equivalent guards.
Every individual test must complete in under 60 seconds. Use bounded waits and close all clients, servers, executors, and other background resources.
Reporter snippets identify what is missing; any added or modified reachability metadata must include appropriate conditions, preferably `typeReached`.
