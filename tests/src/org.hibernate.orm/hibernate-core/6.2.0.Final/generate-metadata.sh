#!/usr/bin/env bash

./gradlew test --tests "org_hibernate_orm.hibernate_core.HibernateH2Test" -Pagent metadataCopy
./gradlew test --tests "org_hibernate_orm.hibernate_core.HibernateDialectTest" -Pagent metadataCopy
