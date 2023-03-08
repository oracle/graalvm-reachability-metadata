#!/usr/bin/env bash

./gradlew deleteGeneratedMetadata
./gradlew test --tests "org_hibernate.hibernate_core.H2DialectHibernateTest" -Pagent metadataCopy
./gradlew test --tests "org_hibernate.hibernate_core.MariaDBDialectHibernateTest" -Pagent metadataCopy
./gradlew test --tests "org_hibernate.hibernate_core.MSSQLDialectHibernateTest" -Pagent metadataCopy
./gradlew test --tests "org_hibernate.hibernate_core.MySQLDialectHibernateTest" -Pagent metadataCopy
./gradlew test --tests "org_hibernate.hibernate_core.OracleDialectHibernateTest" -Pagent metadataCopy
./gradlew test --tests "org_hibernate.hibernate_core.PostgresDialectHibernateTest" -Pagent metadataCopy
./gradlew test --tests "org_hibernate.hibernate_core.HibernateDialectTest" -Pagent metadataCopy
./gradlew test --tests "org_hibernate.hibernate_core.AdditionalMetadataTest" -Pagent metadataCopy
./gradlew test --tests "org_hibernate.hibernate_core.IdentifierGeneratorTest" -Pagent metadataCopy
./gradlew test --tests "org_hibernate.hibernate_core.OptimizerTest" -Pagent metadataCopy
./gradlew updateGeneratedMetadata
