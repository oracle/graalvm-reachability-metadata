# Vendor-dependent coverage

- `WebSphereDataSourceAdapter` requires the IBM WebSphere runtime APIs
  `WSDataSource`, `JDBCConnectionSpec`, and `WSRRAFactory`. They are not available
  in this test project's dependencies. No fake vendor classes or constructor-failure
  tests substitute for an actual WebSphere data source; these call sites remain
  untested.
- `OracleTableMetaDataProviderTest` exercises the supported standard-JDBC fallback
  when Oracle connection APIs are unavailable, using a real H2 database. The
  Oracle-specific synonym flag lookups and invocations require a real Oracle
  connection and remain untested.
