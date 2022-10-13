# Hibernate Validator

The metadata has been gathered by a combination of running the hibernate-validator tests with the agent attached and
by tweaking and completing the output files.

## Run tests with agent

Put this in the `pom.xml`:

```xml
  <profile>
    <id>native</id>
    <build>
      <plugins>
        <plugin>
          <groupId>org.graalvm.buildtools</groupId>
          <artifactId>native-maven-plugin</artifactId>
          <version>0.9.14</version>
          <extensions>true</extensions>
          <configuration>
            <agent>
              <options>
                <option>experimental-conditional-config-part</option>
              </options>
              <options name="test">
                <option>access-filter-file=${project.basedir}/../access-filter.json</option>
              </options>
            </agent>
          </configuration>
        </plugin>
      </plugins>
    </build>
  </profile>
```

`access-filter.json` contains this:

```json
{
  "rules": [
    {
      "includeClasses": "**"
    },
    {
      "excludeClasses": "org.hibernate.validator.test.**"
    },
    {
      "excludeClasses": "org.apache.logging.log4j.**"
    }
  ]
}
```

Use this script to run the tests:

```bash
#!/usr/bin/env bash

set -euo pipefail

echo "Running tests..."
mvn clean test -Pnative -Dagent=true -pl engine

echo "Post-processing native-image files"
INPUT_DIR=(engine/target/native/agent-output/test/session-*)
OUTPUT_DIR="engine/src/main/resources/META-INF/native-image/org.hibernate.validator/hibernate-validator"
mkdir -p "$OUTPUT_DIR"
native-image-configure generate-conditional --user-code-filter=user-code-filter.json --input-dir="$INPUT_DIR" --output-dir="$OUTPUT_DIR"
```

`user-code-filter.json` contains this:

```json
{
  "rules": [
    {
      "excludeClasses": "**"
    },
    {
      "includeClasses": "org.hibernate.validator.**"
    },
    {
      "excludeClasses": "org.hibernate.validator.test.**"
    },
    {
      "excludeClasses": "org.hibernate.validator.testutil.**"
    },
    {
      "excludeClasses": "org.hibernate.validator.testutils.**"
    }
  ]
}
```
