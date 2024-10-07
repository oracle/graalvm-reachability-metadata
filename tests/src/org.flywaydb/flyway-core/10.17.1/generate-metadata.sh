#!/usr/bin/env bash

./gradlew clean
./gradlew test --tests "flyway.FlywayTests" -Pagent
