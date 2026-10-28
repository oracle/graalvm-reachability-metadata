# Druid test coverage

The suite exercises pool statistics and JMX management, logging backends,
configuration and filter resources, SQL firewall rules, HA selectors, snapshot
persistence, and H2 XA transactions through public APIs.

Some optional driver-specific dynamic accesses remain untested:

- `DruidConnectionHolder` inspects Oracle network fields only for a live Oracle
  `T4CConnection`. Its test covers ordinary pool state restoration with H2, not
  those Oracle fields.
- `MySqlUtils` reflects on live MySQL connection internals. Its test covers the
  packaged keyword and type dictionaries, not the driver-specific XA and timing
  methods.
- `JtdsXAResource` requires a real jTDS connection to SQL Server or Sybase. The
  test classpath has neither jTDS nor a matching database, so no stand-in is used.
- `SpringStatUtils.getMethodStatData` looks up a two-argument method but invokes
  it without arguments. The suite tests the supported list and reset paths
  instead of asserting that invocation error.
- `MonitorDaoJdbcImpl` snapshot insertion and SQL-text hashing are exercised
  against H2 in MySQL mode. Its aggregate query's non-grouped columns require
  MySQL's permissive grouping semantics, so the load path is not exercised.
