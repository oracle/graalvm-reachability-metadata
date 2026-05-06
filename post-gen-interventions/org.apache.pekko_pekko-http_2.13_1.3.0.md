# Post-generation intervention report

Library: org.apache.pekko:pekko-http_2.13:1.3.0

Stage: `metadata_fix_failed`

## Summary

The native test failures are metadata-related, so no generated tests were removed. The Gradle excerpt shows Typesafe Config failing in the native image because Pekko configuration resources were not available (`No configuration setting found for key 'pekko'` and missing `file-get-conditional`). The Codex metadata-fix log confirms this was only the first layer: after adding configuration resources and several reflection entries, native execution progressed further and exposed additional Pekko Actor startup classes loaded from configuration/reflection.

## Root cause by failing test

- `requestBuildingMarshalsFormDataAndRemovesModeledHeaders()` failed while creating an `ActorSystem`. The excerpted failure is missing Pekko `reference.conf` resource metadata (`pekko` root config absent). After Codex partially fixed that, this path continued to expose missing reflection/config-loaded Pekko Actor classes.
- `multipartFormDataRoundTripsNamedPartsWithContentTypes()` failed for the same `ActorSystem` startup path and missing Pekko configuration resources, followed by the same cascading actor reflection metadata gaps.
- `marshalAndUnmarshalStrictEntitiesRequestsAndResponses()` failed for the same `ActorSystem` startup path and missing Pekko configuration resources, followed by the same cascading actor reflection metadata gaps.
- `httpServerBindingAndClientRequestsExchangeStrictResponses()` failed for the same `ActorSystem` startup path and missing Pekko configuration resources, followed by the same cascading actor reflection metadata gaps.
- `sealedRoutesApplyPathParametersQueryParametersEntityUnmarshallingAndRejectionHandling()` failed for the same `ActorSystem` startup path and missing Pekko configuration resources, followed by the same cascading actor reflection metadata gaps.
- `streamingSupportSettingsAndServerSentEventsExposeExpectedPublicConfiguration()` failed because `RoutingSettings` could not read Pekko HTTP routing defaults such as `file-get-conditional`; this is also missing `reference.conf` resource metadata, not a test bug.

## Remaining missing metadata

Codex partially fixed the resource issue by adding Pekko configuration resources and then iteratively added several reflective registrations for Pekko Actor startup, including default logging, scheduler/provider/router classes, `sun.misc.Unsafe.theUnsafe`, and `Props$EmptyActor`. The log ends while the final verification run is still in progress, so Codex did not complete the reproduce-fix-verify loop.

The current native test result still shows a metadata gap for a config-loaded Pekko Actor mailbox requirement:

```text
org.apache.pekko.dispatch.BoundedDequeBasedMessageQueueSemantics
```

It is loaded during `org.apache.pekko.dispatch.Mailboxes` initialization from `pekko.actor.mailbox.requirement`, so it requires additional reachability metadata. Codex could not resolve it because each previous metadata addition uncovered the next runtime-configured Pekko Actor component, and the metadata-fix session stopped before reaching a clean native test pass.

## Preservation decision

The generated support should be preserved. The tests exercise real public Pekko HTTP functionality and two tests already pass in native image. The remaining failures identify genuine native-image reachability gaps in Pekko configuration resources and reflectively/configuration-loaded actor classes, so removing the tests would hide valid metadata requirements rather than eliminate unsupported or invalid test coverage.
