# Hibernate ORM

The metadata has been gathered by a combination of running the hibernate-orm:hibernate-core tests for H2, package by package with the agent attached, combining the results and by tweaking and completing the output files.

Additionally extract and manually merge hints provided by the `org.hibernate.orm:hibernate-graalvm` artifact by capturing hints contribuited via `org.hibernate.graalvm.internal.GraalVMStaticAutofeature`.

## Run tests with agent

**Note**: Provide extra memory (`-Xmx12g`) to `native-image-configure`.

```bash
#!/usr/bin/env bash

set -euo pipefail

for PACKAGE_PATH in hibernate-core/src/test/java/org/hibernate/orm/test/*; do

  PACKAGE=$(basename "$PACKAGE_PATH")

  ./gradlew hibernate-core:test --tests "org.hibernate.orm.test.jpa.$PACKAGE.*" -Pdb=h2 -Pagent
  ./gradlew hibernate-core:metadataCopy
done  
```

`access-filter.json` contains this:

```json
{
  "rules": [
    {
      "includeClasses": "**"
    },
    {
      "excludeClasses": "org.hibernate.orm.test.**"
    },
    {
      "excludeClasses": "org.hibernate.testing.**"
    },
    {
      "excludeClasses": "org.apache.logging.log4j.**"
    }
  ]
}

```

`user-code-filter.json` contains this:

```json
{
  "rules": [
    {
      "excludeClasses": "**"
    },
    {
      "includeClasses": "org.hibernate.**"
    },
    {
      "excludeClasses": "org.hibernate.orm.test.**"
    },
    {
      "excludeClasses": "org.hibernate.testing.**"
    },
    {
      "excludeClasses": "net.bytebuddy.**"
    }
  ]
}
```

## Extract AutomaticFeature hints

```java

// trick native environment
HostedManagement hostedManagement = new HostedManagement();
ReflectionTestUtils.setField(HostedManagement.class, "singletonDuringImageBuild", hostedManagement);
Constructor<ReflectionDataBuilder> declaredConstructor = ReflectionDataBuilder.class.getDeclaredConstructor(SubstrateAnnotationExtracter.class);
ReflectionUtils.makeAccessible(declaredConstructor);
ReflectionDataBuilder reflectionDataBuilder = declaredConstructor.newInstance(
    new SubstrateAnnotationExtracter() {
      @Override
      public boolean hasAnnotation(AnnotatedElement element, Class<? extends Annotation> annotationType) {
        return false;
      }
      @Override
      public <T extends Annotation> T extractAnnotation(AnnotatedElement element, Class<T> annotationType, boolean declaredOnly) {
        return null;
      }
    });
ReflectionTestUtils.invokeMethod(hostedManagement, "doAdd", RuntimeReflectionSupport.class, reflectionDataBuilder);

// invoke writing hints
GraalVMStaticAutofeature feature = new GraalVMStaticAutofeature();
feature.beforeAnalysis(null);

// extract & render classes
Map<Class<?>, ReflectionDataBuilder> configObjects = (Map<Class<?>, ReflectionDataBuilder>) ReflectionTestUtils.getField(HostedManagement.get(), "configObjects");
Set<Class<?>> reflectionClasses = (Set<Class<?>>) ReflectionTestUtils.getField(configObjects.get(RuntimeReflectionSupport.class), "reflectionClasses");

reflectionClasses.forEach(type -> {
  System.out.print(
      String.format("""
          {
             "name": "%s",
             "condition": {
               "typeReachable": "org.hibernate.jpa.HibernatePersistenceProvider"
             }
          },
          """, type.getName())
  );
});

// etract & render methods
Map<Executable, Object> reflectionMethods = (Map<Executable, Object>) ReflectionTestUtils.getField(configObjects.get(RuntimeReflectionSupport.class), "reflectionMethods");
MultiValueMap<Class<?>, Executable> methodMap = new LinkedMultiValueMap<>();
reflectionMethods.forEach((method, access) -> {
  methodMap.add(method.getDeclaringClass(), method);
});
methodMap.forEach((type, methods) -> {
  String methodParam = methods.stream()
      .map(method -> {
        String methodName = method.getName().equals(type.getName()) ? "<init>" : method.getName();
            return String.format("    {\n" +
                         "      \"name\": \"%s\",\n"+
                         "      \"parameterTypes\": [%s]\n" +
                         "    }"
                , methodName, Arrays.asList(method.getParameterTypes()).stream().map(it -> "\"" + it.getName() + "\"").collect(Collectors.joining(",")));
          }
      ).collect(Collectors.joining(",\n"));
  System.out.print(
      String.format(
          "{\n"+
            "  \"name\": \"%s\",\n" +
            "  \"queryAllDeclaredMethods\": true,\n" +
            "  \"condition\": {\n" +
            "    \"typeReachable\": \"org.hibernate.jpa.HibernatePersistenceProvider\"\n" +
            "  },\n" +
            "  \"methods\": [\n" +
            "%s\n" +
            "  ]\n" +
          "},\n",type.getName(), methodParam));
});
```

## Manually added hints

- Add reflection entries (ctor) for types listed in `org.hibernate.id.factory.internal.StandardIdentifierGeneratorFactory`.

