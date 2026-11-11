# Vendor-dependent coverage

`OracleTableMetaDataProviderTest` uses the real Oracle JDBC API and an H2
connection to exercise Oracle connection class loading and the documented
standard-JDBC fallback when the connection cannot unwrap to an Oracle
connection. It checks column names, SQL types, nullability, and continued use
of the caller-owned connection. The synonym flag lookups and invocations
remain untested: they need a real Oracle database connection, and this test
project has no approved Oracle database image. No fake vendor types or
connections replace it.

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
