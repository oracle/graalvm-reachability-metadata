#!/usr/bin/env bash

./gradlew deleteGeneratedMetadata
./gradlew test --tests "org_hibernate_orm.hibernate_core.HibernateH2Test" -Pagent metadataCopy
./gradlew test --tests "org_hibernate_orm.hibernate_core.HibernateDialectTest" -Pagent metadataCopy
./gradlew test --tests "org_hibernate_orm.hibernate_core.UrlMessageBundleTest" -Pagent metadataCopy
