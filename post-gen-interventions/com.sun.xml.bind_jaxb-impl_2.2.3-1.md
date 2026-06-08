# Post-generation intervention report

Library: com.sun.xml.bind:jaxb-impl:2.2.3-1
Stage: metadata_fix_failed

## Summary

The native test failures are metadata-related. All reported failures happen before the
individual JAXB assertions can run, while `JAXBContext.newInstance(...)` asks the JAXB API
`ContextFinder` to reflectively invoke the JAXB RI provider method:

```json
{
  "type": "com.sun.xml.bind.v2.ContextFactory",
  "methods": [
    {
      "name": "createContext",
      "parameterTypes": [
        "java.lang.Class[]",
        "java.util.Map"
      ]
    }
  ]
}
```

The Gradle output shows `MissingReflectionRegistrationError` for that method in every
native test failure. This is not a native-image limitation, unsupported platform feature,
or a test-only bug, so no generated tests should be removed for this failure.

## Root cause by failure

Each failing `Jaxb_implTest` method reaches the same missing JAXB RI provider registration:
`com.sun.xml.bind.v2.ContextFactory.createContext(Class[], Map)`.

- `marshalsAndUnmarshalsAnnotatedObjectGraph()` — metadata-related; fails during
  `JAXBContext.newInstance(...)` provider lookup.
- `riMarshallerPropertiesCustomizeNamespacesAndEscaping()` — metadata-related; fails during
  `JAXBContext.newInstance(...)` provider lookup.
- `binderSynchronizesJaxbObjectsAndDomNodes()` — metadata-related; fails during
  `JAXBContext.newInstance(...)` provider lookup.
- `cycleRecoverableSuppliesReplacementForObjectGraphCycles()` — metadata-related; fails during
  `JAXBContext.newInstance(...)` provider lookup.
- `generatesSchemaForAnnotatedModel()` — metadata-related; fails during
  `JAXBContext.newInstance(...)` provider lookup.
- The remaining generated JAXB tests reported in the same native run have the same root cause,
  because the run reports `8 tests failed` and the shared stack traces all enter
  `javax.xml.bind.ContextFinder` before reaching library-specific assertions.

The Codex metadata-fix log also shows later missing reflection/proxy registrations while it
iterated through the JAXB surface, including JAXB annotation proxy registrations involving
`com.sun.xml.bind.v2.model.annotation.Locatable` and schema-generation proxies under
`com.sun.xml.bind.v2.schemagen.xmlschema` such as `Schema` and `Import`. Those are further
metadata gaps in the same library-owned reflective/proxy behavior, not reasons to delete tests.

## Why generated support should be preserved

The generated support exercises real `jaxb-impl` behavior: JAXB context creation, RI marshaller
properties, binder synchronization, ID/IDREF handling, object graph marshalling/unmarshalling,
cycle recovery, element references, and schema generation. These are exactly the paths that
reachability metadata for `jaxb-impl` should cover in native image. Removing the tests would hide
missing reflection/proxy metadata instead of reducing invalid or unsupported coverage.

No metadata files were modified as part of this intervention report, and no generated tests were
removed because the remaining failure is metadata-related.
