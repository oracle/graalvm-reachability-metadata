# Post-generation intervention report

Library: com.sun.xml.bind:jaxb-impl:2.2.3-1
Stage: metadata_fix_failed

## Summary

The native test run failed because JAXB context creation still requires GraalVM reachability metadata. The Gradle output shows `org.graalvm.nativeimage.MissingReflectionRegistrationError` while `javax.xml.bind.ContextFinder` reflectively invokes `com.sun.xml.bind.v2.ContextFactory.createContext(Class[], Map)`. This happens before the individual test logic can exercise JAXB marshalling, unmarshalling, DOM, binder, schema, or adapter behavior.

The failures are metadata-related, so no generated tests were removed and no metadata files were modified during this intervention.

## Root cause by failing test

All seven generated tests have the same root cause: missing or ineffective reflection metadata for JAXB runtime initialization, specifically reflective access to `com.sun.xml.bind.v2.ContextFactory.createContext(java.lang.Class[], java.util.Map)`. Codex also exposed further JAXB `LocatableAnnotation` dynamic proxy gaps for JAXB annotations while iterating, which confirms this is an incomplete metadata fix rather than a test bug or unsupported native-image platform feature.

- `marshalsAndUnmarshalsAnnotatedObjectGraph()` / later reduced `marshalsAnnotatedObjectGraph()` fails during `JAXBContext.newInstance(...)` before object-graph marshalling assertions can run.
- `supportsPolymorphicElementLists()` / later reduced `marshalsPolymorphicElementLists()` fails during `JAXBContext.newInstance(...)` before polymorphic list coverage can run.
- `adaptsMapValuesWithXmlAdapter()` / later reduced `marshalsMapValuesWithXmlAdapter()` fails during `JAXBContext.newInstance(...)` before adapter coverage can run.
- `preservesMixedContentWithElementReferences()` / later reduced `marshalsMixedContentWithElementReferences()` fails during `JAXBContext.newInstance(...)` before mixed-content coverage can run.
- `preservesWildcardDomElements()` / later reduced `marshalsWildcardDomElements()` fails during `JAXBContext.newInstance(...)`; Codex also identified a related `W3CDomHandler` constructor metadata need for the wildcard DOM path.
- `updatesDomWithBinder()` / later reduced `marshalsToDomDocument()` fails during `JAXBContext.newInstance(...)` before DOM/Binder behavior can run.
- `generatesSchemaAndUsesItForUnmarshalling()` / later reduced `generatesSchemaForAnnotatedObjectGraph()` fails during `JAXBContext.newInstance(...)` before schema-generation coverage can run.

## Metadata still missing or incomplete

The remaining support needs complete JAXB reflection/proxy metadata, including:

- reflection access for `com.sun.xml.bind.v2.ContextFactory.createContext(Class[], Map)` with a condition that is active when `javax.xml.bind.ContextFinder` performs provider discovery;
- dynamic proxy registrations for JAXB annotation interfaces paired with `com.sun.xml.bind.v2.model.annotation.Locatable`, such as `XmlID` and the other JAXB annotations exercised by the generated model classes;
- reflection access for JAXB DOM handling such as `javax.xml.bind.annotation.W3CDomHandler.<init>()` when `@XmlAnyElement` is exercised.

The Codex metadata-fix log shows Codex was still in an iterative metadata-repair loop. It added several candidate metadata entries and then planned another pinned-GraalVM verification run, but the log ends immediately after JSON validation with a recorder error: `No space left on device (os error 28)`. Codex therefore did not complete a successful verify cycle and did not prove that the final metadata set was sufficient.

## Why the generated support should be preserved

The generated tests exercise meaningful `jaxb-impl` behavior: JAXB provider discovery, annotation-driven model introspection, adapters, ID/IDREF handling, polymorphic elements, mixed content, wildcard DOM elements, DOM marshalling, Binder behavior, and schema generation. The observed failures occur at native-image metadata boundaries before those behaviors can be validated. Removing the tests would hide real reachability metadata gaps for JAXB rather than eliminating unsupported or invalid test coverage, so the remaining generated support should be preserved for a future metadata-only fix.
