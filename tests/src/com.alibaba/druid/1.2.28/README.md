# Druid test coverage

The suite exercises pool statistics and JMX management, logging backends,
configuration and filter resources, SQL firewall rules, HA selectors, snapshot
persistence, and XA transactions through public APIs.

Driver-specific tests use the declared Docker images for Oracle connection
replacement, MySQL connection identity, packet timing and both XA pinning modes,
and jTDS resource-manager identity and emulated XA transactions against SQL
Server. Monitor snapshot loading uses MySQL with permissive grouping, as required
by the DAO's aggregate queries, and verifies SQL-text lookup from both the
constant table and the DAO cache.

`FilterManager` tests exercise both alias-based filters and a long fully
qualified application filter name. `SpringStatUtils` tests select, list and
reset method statistics from an application-specific `SpringStat` subclass.
`Utils` tests cover the context-loader fallback for an absent optional extension;
they do not introduce classes through custom loaders.

Some optional driver-specific dynamic accesses remain untested:

- `MySqlUtils` has branches for older MySQL Connector/J APIs that cannot be
  exercised with the modern connector used here. The suite does not replace
  driver internals with stand-ins or load competing connector versions.
