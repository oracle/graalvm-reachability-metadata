# Post-generation intervention report

Library: jdom:jdom:1.0b8

Stage: metadata_fix_failed

## Summary

The Gradle native test run failed because generated tests reached JDOM code paths that require additional GraalVM reachability metadata. The failures are metadata-related `MissingReflectionRegistrationError` cases, not native-image limitations, unsupported platform features, or broken tests. No generated tests were removed.

The Codex metadata-fix log shows that the first run had 8 failing tests. Codex added several metadata candidates and reduced the failure set to the `ElementTest` serialization path, but it did not complete a passing verification run. The remaining unresolved metadata is around Java serialization of JDOM `Element` internals, ultimately reporting a missing reflective invocation of the protected `java.util.AbstractList()` constructor.

## Failure root causes

- `AttributeTest.serializesAttributeWithNamespace()` failed while Java serialization inspected `org.jdom.Attribute`. Missing metadata: reflective field access for `org.jdom.Attribute.type`.
- `DOMOutputterTest.outputsDocumentWithJaxpAdapterDiscoveredByDefaultConstructor()` failed because `org.jdom.output.DOMOutputter` reflectively instantiates `org.jdom.adapters.JAXPDOMAdapter`. Missing metadata: public no-arg constructor for `org.jdom.adapters.JAXPDOMAdapter`.
- `DOMOutputterTest.outputsDocumentWithExplicitDomAdapterClass()` failed for the same JDOM reflective adapter construction path. Missing metadata: public no-arg constructor for `org.jdom.adapters.JAXPDOMAdapter`.
- `ElementTest.serializesElementNamespaceAndClonesAdditionalNamespaceDeclarations()` failed in Java serialization of `org.jdom.Element`. Initial missing metadata included reflective field access for `org.jdom.Element.attributes`; subsequent Codex runs progressed to `java.util.ArrayList` serialization details and then the still-unresolved protected `java.util.AbstractList()` constructor.
- `JDOMExceptionTest.includesServletExceptionRootCauseInNestedMessage()` failed because `org.jdom.JDOMException` reflectively invokes `javax.servlet.ServletException.getRootCause()`. Missing metadata: `javax.servlet.ServletException#getRootCause()`.
- `OracleV2DOMAdapterTest.parsesDocumentThroughOracleV2AdapterLoadedInIsolatedClassLoader()` failed because `org.jdom.adapters.OracleV2DOMAdapter` reflectively invokes `oracle.xml.parser.v2.DOMParser.parse(InputSource)`. Missing metadata: `oracle.xml.parser.v2.DOMParser#parse(org.xml.sax.InputSource)`.
- `OracleV2DOMAdapterTest.parsesNamespaceAwareDocumentThroughOracleV2ParserReflection()` failed for the same Oracle V2 parser reflection path. Missing metadata: `oracle.xml.parser.v2.DOMParser#parse(org.xml.sax.InputSource)`.
- `OracleV2DOMAdapterTest.reportsSaxParseFailuresFromOracleV2ParserReflection()` failed for the same Oracle V2 parser reflection path. Missing metadata: `oracle.xml.parser.v2.DOMParser#parse(org.xml.sax.InputSource)`.

## Why Codex could not finish the metadata fix

Codex correctly identified the failures as missing metadata and iterated through the suggested registrations. The easy reflection gaps were covered during the attempt, but the `ElementTest` serialization case kept advancing through deeper serialization requirements. The log records this progression: `org.jdom.Element.attributes`, `java.util.ArrayList.serialVersionUID`, `java.util.ArrayList.size`, private `java.util.ArrayList.writeObject(ObjectOutputStream)`, and finally the protected `java.util.AbstractList()` constructor.

The unresolved part is therefore not a bad generated test; it is incomplete metadata for the serialized object graph used by JDOM `1.0b8`, where `AttributeList` and `ContentList` interact with JDK list serialization. Codex tried multiple metadata shapes for `AbstractList`, `ArrayList`, `AttributeList`, and `ContentList`, but did not reach a verified passing run before the metadata-fix workflow ended.

## Preservation rationale

The remaining generated support should be preserved because it exercises real JDOM behavior: reflective DOM adapter loading, Oracle parser reflection, servlet/naming exception root-cause handling, XPath integration, and serialization of JDOM model objects. The initial run already had 20 successful tests, and the Codex iterations reduced the failures to one serialization-focused metadata gap. Removing these tests would hide valid reachability requirements rather than fix a non-metadata problem.
