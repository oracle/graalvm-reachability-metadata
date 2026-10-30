# Vendor-dependent coverage

- `WebSphereDataSourceAdapterTest` uses the real IBM WebSphere heritage JDBC API
  dependency to exercise the documented rejection of a non-WebSphere target data
  source. Construction resolves `WSDataSource`, `JDBCConnectionSpec`, `WSRRAFactory`,
  and their JDBC methods. Connection creation remains untested: the API artifact
  does not include the `JDBCConnectionSpecImpl` runtime implementation required by
  `WSRRAFactory.createJDBCConnectionSpec()`. No fake vendor types substitute for it.
- `OracleTableMetaDataProviderTest` exercises the supported standard-JDBC fallback
  when Oracle connection APIs are unavailable, using a real H2 database. The
  Oracle-specific synonym flag lookups and invocations require a real Oracle
  connection and remain untested.
