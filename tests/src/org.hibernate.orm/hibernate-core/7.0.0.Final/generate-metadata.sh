#!/usr/bin/env bash

../../../../../gradlew deleteGeneratedMetadata
../../../../../gradlew test --tests "org_hibernate_orm.hibernate_core.H2DialectHibernateTest" -Pagent metadataCopy
../../../../../gradlew test --tests "org_hibernate_orm.hibernate_core.MariaDBDialectHibernateTest" -Pagent metadataCopy
../../../../../gradlew test --tests "org_hibernate_orm.hibernate_core.MSSQLDialectHibernateTest" -Pagent metadataCopy
../../../../../gradlew test --tests "org_hibernate_orm.hibernate_core.MySQLDialectHibernateTest" -Pagent metadataCopy
../../../../../gradlew test --tests "org_hibernate_orm.hibernate_core.OracleDialectHibernateTest" -Pagent metadataCopy
../../../../../gradlew test --tests "org_hibernate_orm.hibernate_core.PostgresDialectHibernateTest" -Pagent metadataCopy
../../../../../gradlew test --tests "org_hibernate_orm.hibernate_core.HibernateDialectTest" -Pagent metadataCopy
../../../../../gradlew test --tests "org_hibernate_orm.hibernate_core.LoggerTest" -Pagent metadataCopy
../../../../../gradlew test --tests "org_hibernate_orm.hibernate_core.IdentifierGeneratorTest" -Pagent metadataCopy
../../../../../gradlew test --tests "org_hibernate_orm.hibernate_core.OptimizerTest" -Pagent metadataCopy
../../../../../gradlew test --tests "org_hibernate_orm.hibernate_core.EntityManagerTest" -Pagent metadataCopy
../../../../../gradlew updateGeneratedMetadata
