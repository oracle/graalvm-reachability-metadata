# Post-generation intervention report

Library: org.mockito:mockito-core:5.0.0
Stage: metadata_fix_failed

## Summary

The metadata-fix workflow did not fully resolve the native test run. After Codex ran metadata generation/fix attempts, `./gradlew test -Pcoordinates=org.mockito:mockito-core:5.0.0` still failed in three generated native tests:

- `KotlinInlineClassUtilTest > stubbingMethodReturningUnderlyingTypeUnboxesKotlinInlineClassValue()`
- `ModuleHandlerInnerModuleSystemFoundTest > subclassMockMakerLooksUpInjectionBaseWithChildContextLoader()`
- `ModuleHandlerInnerModuleSystemFoundTest > subclassMockMakerAdjustsModuleGraphForNonExportedJdkZipfsProvider()`

All three failures are metadata-related. The failures occur while Byte Buddy/Mockito initializes classes needed for mock generation in the native image:

- `net.bytebuddy.description.type.TypeDescription$ForLoadedType`
- `net.bytebuddy.implementation.bind.MethodDelegationBinder$AmbiguityResolver`

The Gradle output reports `NoClassDefFoundError: Could not initialize class ...` for these Byte Buddy classes. Codex also reported that the remaining failures were in Byte Buddy native runtime initialization after the plain metadata generation command succeeded.

## Root cause

These are not test bugs and not generated tests that should be removed. The generated tests exercise real Mockito behavior that reaches Byte Buddy internals in native image:

- The Kotlin inline-class test reaches Byte Buddy type-description/class-loading infrastructure.
- The module-handler tests reach Mockito's subclass mock maker, which initializes Byte Buddy delegation and ambiguity-resolution infrastructure.

The remaining missing support is deeper Byte Buddy runtime-initialization metadata. Codex added or attempted to add metadata for earlier missing registrations, mock-maker construction, proxies, resources, and test-only support, but it did not finish the full native runtime loop. The final failure no longer presents as a simple first-order `Missing*RegistrationError`; it surfaces as failed static initialization of Byte Buddy classes, which means additional dependent Byte Buddy classes/proxies/reflection entries are still needed around `TypeDescription$ForLoadedType` and `MethodDelegationBinder$AmbiguityResolver`.

No generated tests were removed because the observed failures are metadata-related.

## Why the remaining generated support should be preserved

The passing generated tests already validate useful Mockito native-image coverage, including member-accessor reflection, plugin loading failure proxies, runner construction, spy annotation processing, serialization support, Java 8 utility behavior, and stack-walker paths. The three remaining failures are from incomplete metadata for Byte Buddy/Mockito internals, not from invalid test scenarios.

Preserving the generated support keeps the successful coverage and gives the next metadata-fix pass concrete reproducers for the unresolved Byte Buddy initialization metadata instead of discarding valid Mockito behavior.