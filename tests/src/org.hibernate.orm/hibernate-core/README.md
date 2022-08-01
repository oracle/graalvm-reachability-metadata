# Hibernate ORM

The metadata has been gathered by a combination of running the hibernate-orm:hibernate-core tests for H2, package by package with the agent attached, combining the results and by tweaking and completing the output files.

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

