
# Hibernate ORM

The metadata has been gathered by executing the following commands:

```bash
cd ${PROJECT_BASE_DIR}/graalvm-reachability-metadata/tests/src/org.hibernate/hibernate-core/5.6.14.Final
./gradlew \
    test --tests "org_hibernate.hibernate_core.H2DialectHibernateTest" -Pagent \
    metadataCopy \
    test --tests "org_hibernate.hibernate_core.MSSQLDialectHibernateTest" -Pagent \
    metadataCopy \
    test --tests "org_hibernate.hibernate_core.MySQLDialectHibernateTest" -Pagent \
    metadataCopy \
    test --tests "org_hibernate.hibernate_core.OracleDialectHibernateTest" -Pagent \
    metadataCopy
```