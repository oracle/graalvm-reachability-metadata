#!/usr/bin/env bash

./gradlew clean
./gradlew test --tests "org_jooq.jooq.JooqTest" -Pagent
./gradlew updateGeneratedMetadata
