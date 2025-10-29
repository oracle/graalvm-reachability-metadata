#!/usr/bin/env bash

./gradlew clean
./gradlew test --tests "org_liquibase.liquibase_core.LiquibaseCoreTest" -Pagent
