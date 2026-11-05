# Vendor-dependent coverage

- `WebSphereDataSourceAdapterTest` uses the real IBM WebSphere heritage JDBC API
  dependency to exercise the documented rejection of a non-WebSphere target data
  source. Construction resolves `WSDataSource`, `JDBCConnectionSpec`, `WSRRAFactory`,
  and their JDBC methods. Connection creation remains untested: the API artifact
  does not include the `JDBCConnectionSpecImpl` runtime implementation required by
  `WSRRAFactory.createJDBCConnectionSpec()`. No fake vendor types substitute for it.
- `OracleTableMetaDataProviderTest` uses the real Oracle JDBC API dependency and
  an H2 connection to exercise Oracle API class loading followed by the supported
  standard-JDBC fallback when the connection cannot unwrap to an Oracle connection.
  It verifies column metadata and continued use of the caller-owned connection.
  The Oracle-specific synonym flag lookups and invocations require a real Oracle
  connection and remain untested: the approved Docker image list contains no
  Oracle database image. No fake vendor types or connections substitute for it.
