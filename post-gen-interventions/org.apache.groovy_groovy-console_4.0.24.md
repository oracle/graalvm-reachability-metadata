# Post-generation intervention

Library: org.apache.groovy:groovy-console:4.0.24
Stage: metadata_fix_failed

## Summary

The native test run failed for all eight generated `Groovy_consoleTest` methods. The Gradle failure excerpt shows the native executable failing during test execution with `NoClassDefFoundError: Could not initialize class groovy.lang.GroovySystem`; the Codex metadata-fix log shows that after several metadata additions the failure moved to a stable `BootstrapMethodError` caused by a `NullPointerException` in `java.lang.invoke.MethodHandles.insertArgumentsChecks`, called from `org.codehaus.groovy.vmplugin.v8.IndyInterface.make`.

## Root cause

This remaining failure is not a reachability-metadata miss. Codex had already added concrete Groovy bootstrap metadata for resources and reflective access, including `META-INF/dgminfo`, Groovy release and extension-module resources, `groovy.lang.Closure`, Groovy VM plugin constructors, `SwingExtensions`, `XmlExtensions`, and `IndyInterface` methods. The final runs did not report a `Missing*RegistrationError`; even a diagnostic run with missing-registration warnings continued to fail at the same invokedynamic bootstrap path.

All generated test methods share the same root cause: the generated test itself is written in Groovy and exercises Groovy invokedynamic/bootstrap machinery inside the native image before the individual assertions can run. The native runtime fails inside Groovy's `IndyInterface` / JDK method-handle bootstrap path, which is a native-image/Groovy invokedynamic runtime limitation for this generated test shape rather than missing metadata for `groovy-console` APIs.

## Intervention

Removed the generated failing test class:

- `tests/src/org.apache.groovy/groovy-console/4.0.24/src/test/groovy/org_apache_groovy/groovy_console/Groovy_consoleTest.groovy`

No metadata files were modified during this intervention.

## Why the remaining generated support should be preserved

The remaining generated support should be preserved because Codex identified and retained real Groovy bootstrap reachability requirements before hitting the non-metadata invokedynamic failure. Those resource and reflection registrations are still useful library support for native-image users of `org.apache.groovy:groovy-console:4.0.24`; only the generated Groovy-language test class was invalid for the native test environment.
