# Post-generation intervention report

Library: org.hibernate:hibernate-jpamodelgen:6.5.0.Final

Stage: `metadata_fix_failed`

## Summary

The native test failure was caused by `org_hibernate.hibernate_jpamodelgen.MockerTest > variadicCreatesMockUsingMatchingConstructorArguments()`. The visible failure was:

```text
java.lang.NoClassDefFoundError: Could not initialize class net.bytebuddy.description.type.TypeDescription$ForLoadedType
```

The Codex metadata-fix log shows this was order-dependent: running the `variadic` test alone passed, but running `nullaryCreatesMockWithDefaultAbstractMethodValues()` before it poisoned Byte Buddy initialization state. A temporary diagnostic run without the test's catch block exposed the underlying cause:

```text
com.oracle.svm.core.jdk.UnsupportedFeatureError: Classes cannot be defined at runtime by default when using ahead-of-time Native Image compilation. Tried to define class:

net.bytebuddy.utility.Invoker$Dispatcher
```

This is not a missing reachability metadata registration. It is a Native Image limitation around runtime class definition by Byte Buddy. Codex attempted metadata-oriented fixes, including adding reflection/predefined-class related entries, but the failure pattern remained unchanged because the operation itself requires runtime class generation support rather than ordinary reflection/resource/JNI metadata.

## Intervention

Removed the generated `MockerTest`, because both generated test methods exercise `org.hibernate.processor.validation.Mocker`, which uses Byte Buddy and attempts runtime class definition in the native executable. Also removed the temporary generated diagnostic test file that was only used to uncover the hidden `UnsupportedFeatureError`.

No metadata files were modified as part of this intervention.

## Remaining generated support

The remaining generated tests should be preserved. `JpaDescriptorParserTest` and `XmlParserHelperTest` exercise Hibernate JPAModelGen behavior that is compatible with Native Image, including XML descriptor parsing, schema/resource loading, and related reflective/resource access. After removing only the unsupported Byte Buddy runtime-generation coverage, the coordinate test passes with those remaining tests intact.

Verification command run:

```bash
./gradlew test -Pcoordinates=org.hibernate:hibernate-jpamodelgen:6.5.0.Final --stacktrace
```

Result: passed.
