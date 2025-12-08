#!/usr/bin/env bash
# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

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
