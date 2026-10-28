# Oracle JDBC coverage

`DefaultConnectionFinderTest` unwraps real Oracle JDBC connection wrappers to
retrieve the underlying driver connection. The connection is configured but
never logged on, so this test needs neither a database nor an OCI installation.

`OracleJDBCTypeFactory` remains uncovered: its public conversion methods resolve
`MDSYS.SDO_GEOMETRY`, `MDSYS.SDO_POINT_TYPE`, `MDSYS.SDO_ELEM_INFO_ARRAY`, and
`MDSYS.SDO_ORDINATE_ARRAY` descriptors through an Oracle database connection.
The repository currently has no approved Oracle Docker image. Enabling those
tests requires approving an Oracle database image in the shared allowlist first;
that shared infrastructure is outside this pass's test-project-only edit scope.
No descriptor substitutes, reflection shortcuts, or constructor-only tests are
used in place of actual geometry conversion.
