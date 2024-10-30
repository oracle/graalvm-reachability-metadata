#!/usr/bin/env bash

../../../../../gradlew clean
../../../../../gradlew deleteGeneratedMetadata
../../../../../gradlew test --tests "org_liquibase.liquibase_core.LiquibaseCoreTest" -Pagent metadataCopy