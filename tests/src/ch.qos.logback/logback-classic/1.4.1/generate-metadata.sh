#!/usr/bin/env bash

./gradlew clean
./gradlew test --tests "org.graalvm.logback.LogbackTests" -Pagent
./gradlew updateGeneratedMetadata
