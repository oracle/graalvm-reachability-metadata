# Vendor-dependent coverage

`OracleTableMetaDataProviderTest` uses the real Oracle JDBC API and an H2
connection to exercise Oracle connection class loading and the documented
standard-JDBC fallback when the connection cannot unwrap to an Oracle
connection. A second test starts the approved, pre-pulled Oracle Free image,
creates a table and a private synonym, and discovers the synonym's columns
through `OracleTableMetaDataProvider`. It checks column names, SQL types,
nullability, and restoration of both initial values of the connection's
include-synonyms setting. An insert through the synonym verifies continued use
of the caller-owned connection. No fake vendor types or connections replace it.

# Reporter-requested infrastructure

`Spring_jdbcTest` configures a `DelegatingDataSource`, `JdbcTemplate` (including
inherited `JdbcAccessor` properties), and `JdbcTransactionManager` through
Spring bean definitions. A `JdbcClient` bean is created through its public
static factory, yielding the library's `DefaultJdbcClient` implementation.
Named and positional parameter queries verify committed inserts and rolled-back
updates against a real in-memory database. Bean property population, factory
method resolution, type-based bean lookup, and inferred lifecycle handling
exercise the normal container-managed JDBC configuration without test-side
reflection or references to the package-private client implementation.
