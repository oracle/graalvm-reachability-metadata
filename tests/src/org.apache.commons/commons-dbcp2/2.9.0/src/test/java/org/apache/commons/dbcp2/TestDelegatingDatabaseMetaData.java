/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.commons.dbcp2;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SuppressWarnings("FieldCanBeLocal")
public class TestDelegatingDatabaseMetaData {

    private TesterConnection testConn;
    private DelegatingConnection<?> conn;
    private DelegatingDatabaseMetaData delegate;
    private DatabaseMetaData obj;

    @BeforeEach
    public void setUp() throws Exception {
        obj = mock(DatabaseMetaData.class);
        testConn = new TesterConnection("test", "test");
        conn = new DelegatingConnection<>(testConn);
        delegate = new DelegatingDatabaseMetaData(conn, obj);
    }

    @Test
    public void testAllProceduresAreCallable() throws Exception {
        try {
            delegate.allProceduresAreCallable();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).allProceduresAreCallable();
    }

    @Test
    public void testAllTablesAreSelectable() throws Exception {
        try {
            delegate.allTablesAreSelectable();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).allTablesAreSelectable();
    }

    @Test
    public void testAutoCommitFailureClosesAllResultSets() throws Exception {
        try {
            delegate.autoCommitFailureClosesAllResultSets();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).autoCommitFailureClosesAllResultSets();
    }

    @Test
    public void testCheckOpen() throws Exception {
        delegate = new DelegatingDatabaseMetaData(conn, conn.getMetaData());
        final ResultSet rst = delegate.getSchemas();
        assertFalse(rst.isClosed());
        conn.close();
        assertTrue(rst.isClosed());
    }

    @Test
    public void testDataDefinitionCausesTransactionCommit() throws Exception {
        try {
            delegate.dataDefinitionCausesTransactionCommit();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).dataDefinitionCausesTransactionCommit();
    }

    @Test
    public void testDataDefinitionIgnoredInTransactions() throws Exception {
        try {
            delegate.dataDefinitionIgnoredInTransactions();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).dataDefinitionIgnoredInTransactions();
    }

    @Test
    public void testDeletesAreDetectedInteger() throws Exception {
        try {
            delegate.deletesAreDetected(1);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).deletesAreDetected(1);
    }

    @Test
    public void testDoesMaxRowSizeIncludeBlobs() throws Exception {
        try {
            delegate.doesMaxRowSizeIncludeBlobs();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).doesMaxRowSizeIncludeBlobs();
    }

    @Test
    public void testGeneratedKeyAlwaysReturned() throws Exception {
        try {
            delegate.generatedKeyAlwaysReturned();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).generatedKeyAlwaysReturned();
    }

    @Test
    public void testGetAttributesStringStringStringString() throws Exception {
        try {
            delegate.getAttributes("foo", "foo", "foo", "foo");
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getAttributes("foo", "foo", "foo", "foo");
    }

    @Test
    public void testGetBestRowIdentifierStringStringStringIntegerBoolean() throws Exception {
        try {
            delegate.getBestRowIdentifier("foo", "foo", "foo", 1, Boolean.TRUE);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getBestRowIdentifier("foo", "foo", "foo", 1, Boolean.TRUE);
    }

    @Test
    public void testGetCatalogs() throws Exception {
        try {
            delegate.getCatalogs();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getCatalogs();
    }

    @Test
    public void testGetCatalogSeparator() throws Exception {
        try {
            delegate.getCatalogSeparator();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getCatalogSeparator();
    }

    @Test
    public void testGetCatalogTerm() throws Exception {
        try {
            delegate.getCatalogTerm();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getCatalogTerm();
    }

    @Test
    public void testGetClientInfoProperties() throws Exception {
        try {
            delegate.getClientInfoProperties();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getClientInfoProperties();
    }

    @Test
    public void testGetColumnPrivilegesStringStringStringString() throws Exception {
        try {
            delegate.getColumnPrivileges("foo", "foo", "foo", "foo");
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getColumnPrivileges("foo", "foo", "foo", "foo");
    }

    @Test
    public void testGetColumnsStringStringStringString() throws Exception {
        try {
            delegate.getColumns("foo", "foo", "foo", "foo");
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getColumns("foo", "foo", "foo", "foo");
    }

    @Test
    public void testGetConnection() throws Exception {
        try {
            delegate.getConnection();
        } catch (final SQLException e) {
        }
        verify(obj, times(0)).getConnection();
    }

    @Test
    public void testGetCrossReferenceStringStringStringStringStringString() throws Exception {
        try {
            delegate.getCrossReference("foo", "foo", "foo", "foo", "foo", "foo");
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getCrossReference("foo", "foo", "foo", "foo", "foo", "foo");
    }

    @Test
    public void testGetDatabaseMajorVersion() throws Exception {
        try {
            delegate.getDatabaseMajorVersion();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getDatabaseMajorVersion();
    }

    @Test
    public void testGetDatabaseMinorVersion() throws Exception {
        try {
            delegate.getDatabaseMinorVersion();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getDatabaseMinorVersion();
    }

    @Test
    public void testGetDatabaseProductName() throws Exception {
        try {
            delegate.getDatabaseProductName();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getDatabaseProductName();
    }

    @Test
    public void testGetDatabaseProductVersion() throws Exception {
        try {
            delegate.getDatabaseProductVersion();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getDatabaseProductVersion();
    }

    @Test
    public void testGetDefaultTransactionIsolation() throws Exception {
        try {
            delegate.getDefaultTransactionIsolation();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getDefaultTransactionIsolation();
    }

    @Test
    public void testGetDelegate() {
        assertEquals(obj, delegate.getDelegate());
    }

    @Test
    public void testGetDriverMajorVersion() {
        delegate.getDriverMajorVersion();
        verify(obj, times(1)).getDriverMajorVersion();
    }

    @Test
    public void testGetDriverMinorVersion() {
        delegate.getDriverMinorVersion();
        verify(obj, times(1)).getDriverMinorVersion();
    }

    @Test
    public void testGetDriverName() throws Exception {
        try {
            delegate.getDriverName();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getDriverName();
    }

    @Test
    public void testGetDriverVersion() throws Exception {
        try {
            delegate.getDriverVersion();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getDriverVersion();
    }

    @Test
    public void testGetExportedKeysStringStringString() throws Exception {
        try {
            delegate.getExportedKeys("foo", "foo", "foo");
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getExportedKeys("foo", "foo", "foo");
    }

    @Test
    public void testGetExtraNameCharacters() throws Exception {
        try {
            delegate.getExtraNameCharacters();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getExtraNameCharacters();
    }

    @Test
    public void testGetFunctionColumnsStringStringStringString() throws Exception {
        try {
            delegate.getFunctionColumns("foo", "foo", "foo", "foo");
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getFunctionColumns("foo", "foo", "foo", "foo");
    }

    @Test
    public void testGetFunctionsStringStringString() throws Exception {
        try {
            delegate.getFunctions("foo", "foo", "foo");
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getFunctions("foo", "foo", "foo");
    }

    @Test
    public void testGetIdentifierQuoteString() throws Exception {
        try {
            delegate.getIdentifierQuoteString();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getIdentifierQuoteString();
    }

    @Test
    public void testGetImportedKeysStringStringString() throws Exception {
        try {
            delegate.getImportedKeys("foo", "foo", "foo");
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getImportedKeys("foo", "foo", "foo");
    }

    @Test
    public void testGetIndexInfoStringStringStringBooleanBoolean() throws Exception {
        try {
            delegate.getIndexInfo("foo", "foo", "foo", Boolean.TRUE, Boolean.TRUE);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getIndexInfo("foo", "foo", "foo", Boolean.TRUE, Boolean.TRUE);
    }

    @Test
    public void testGetJDBCMajorVersion() throws Exception {
        try {
            delegate.getJDBCMajorVersion();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getJDBCMajorVersion();
    }

    @Test
    public void testGetJDBCMinorVersion() throws Exception {
        try {
            delegate.getJDBCMinorVersion();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getJDBCMinorVersion();
    }

    @Test
    public void testGetMaxBinaryLiteralLength() throws Exception {
        try {
            delegate.getMaxBinaryLiteralLength();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getMaxBinaryLiteralLength();
    }

    @Test
    public void testGetMaxCatalogNameLength() throws Exception {
        try {
            delegate.getMaxCatalogNameLength();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getMaxCatalogNameLength();
    }

    @Test
    public void testGetMaxCharLiteralLength() throws Exception {
        try {
            delegate.getMaxCharLiteralLength();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getMaxCharLiteralLength();
    }

    @Test
    public void testGetMaxColumnNameLength() throws Exception {
        try {
            delegate.getMaxColumnNameLength();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getMaxColumnNameLength();
    }

    @Test
    public void testGetMaxColumnsInGroupBy() throws Exception {
        try {
            delegate.getMaxColumnsInGroupBy();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getMaxColumnsInGroupBy();
    }

    @Test
    public void testGetMaxColumnsInIndex() throws Exception {
        try {
            delegate.getMaxColumnsInIndex();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getMaxColumnsInIndex();
    }

    @Test
    public void testGetMaxColumnsInOrderBy() throws Exception {
        try {
            delegate.getMaxColumnsInOrderBy();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getMaxColumnsInOrderBy();
    }

    @Test
    public void testGetMaxColumnsInSelect() throws Exception {
        try {
            delegate.getMaxColumnsInSelect();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getMaxColumnsInSelect();
    }

    @Test
    public void testGetMaxColumnsInTable() throws Exception {
        try {
            delegate.getMaxColumnsInTable();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getMaxColumnsInTable();
    }

    @Test
    public void testGetMaxConnections() throws Exception {
        try {
            delegate.getMaxConnections();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getMaxConnections();
    }

    @Test
    public void testGetMaxCursorNameLength() throws Exception {
        try {
            delegate.getMaxCursorNameLength();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getMaxCursorNameLength();
    }

    @Test
    public void testGetMaxIndexLength() throws Exception {
        try {
            delegate.getMaxIndexLength();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getMaxIndexLength();
    }

    @Test
    public void testGetMaxLogicalLobSize() throws Exception {
        try {
            delegate.getMaxLogicalLobSize();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getMaxLogicalLobSize();
    }

    @Test
    public void testGetMaxProcedureNameLength() throws Exception {
        try {
            delegate.getMaxProcedureNameLength();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getMaxProcedureNameLength();
    }

    @Test
    public void testGetMaxRowSize() throws Exception {
        try {
            delegate.getMaxRowSize();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getMaxRowSize();
    }

    @Test
    public void testGetMaxSchemaNameLength() throws Exception {
        try {
            delegate.getMaxSchemaNameLength();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getMaxSchemaNameLength();
    }

    @Test
    public void testGetMaxStatementLength() throws Exception {
        try {
            delegate.getMaxStatementLength();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getMaxStatementLength();
    }

    @Test
    public void testGetMaxStatements() throws Exception {
        try {
            delegate.getMaxStatements();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getMaxStatements();
    }

    @Test
    public void testGetMaxTableNameLength() throws Exception {
        try {
            delegate.getMaxTableNameLength();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getMaxTableNameLength();
    }

    @Test
    public void testGetMaxTablesInSelect() throws Exception {
        try {
            delegate.getMaxTablesInSelect();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getMaxTablesInSelect();
    }

    @Test
    public void testGetMaxUserNameLength() throws Exception {
        try {
            delegate.getMaxUserNameLength();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getMaxUserNameLength();
    }

    @Test
    public void testGetNumericFunctions() throws Exception {
        try {
            delegate.getNumericFunctions();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getNumericFunctions();
    }

    @Test
    public void testGetPrimaryKeysStringStringString() throws Exception {
        try {
            delegate.getPrimaryKeys("foo", "foo", "foo");
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getPrimaryKeys("foo", "foo", "foo");
    }

    @Test
    public void testGetProcedureColumnsStringStringStringString() throws Exception {
        try {
            delegate.getProcedureColumns("foo", "foo", "foo", "foo");
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getProcedureColumns("foo", "foo", "foo", "foo");
    }

    @Test
    public void testGetProceduresStringStringString() throws Exception {
        try {
            delegate.getProcedures("foo", "foo", "foo");
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getProcedures("foo", "foo", "foo");
    }

    @Test
    public void testGetProcedureTerm() throws Exception {
        try {
            delegate.getProcedureTerm();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getProcedureTerm();
    }

    @Test
    public void testGetPseudoColumnsStringStringStringString() throws Exception {
        try {
            delegate.getPseudoColumns("foo", "foo", "foo", "foo");
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getPseudoColumns("foo", "foo", "foo", "foo");
    }

    @Test
    public void testGetResultSetHoldability() throws Exception {
        try {
            delegate.getResultSetHoldability();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getResultSetHoldability();
    }

    @Test
    public void testGetRowIdLifetime() throws Exception {
        try {
            delegate.getRowIdLifetime();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getRowIdLifetime();
    }

    @Test
    public void testGetSchemas() throws Exception {
        try {
            delegate.getSchemas();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getSchemas();
    }

    @Test
    public void testGetSchemasStringString() throws Exception {
        try {
            delegate.getSchemas("foo", "foo");
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getSchemas("foo", "foo");
    }

    @Test
    public void testGetSchemaTerm() throws Exception {
        try {
            delegate.getSchemaTerm();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getSchemaTerm();
    }

    @Test
    public void testGetSearchStringEscape() throws Exception {
        try {
            delegate.getSearchStringEscape();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getSearchStringEscape();
    }

    @Test
    public void testGetSQLKeywords() throws Exception {
        try {
            delegate.getSQLKeywords();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getSQLKeywords();
    }

    @Test
    public void testGetSQLStateType() throws Exception {
        try {
            delegate.getSQLStateType();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getSQLStateType();
    }

    @Test
    public void testGetStringFunctions() throws Exception {
        try {
            delegate.getStringFunctions();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getStringFunctions();
    }

    @Test
    public void testGetSuperTablesStringStringString() throws Exception {
        try {
            delegate.getSuperTables("foo", "foo", "foo");
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getSuperTables("foo", "foo", "foo");
    }

    @Test
    public void testGetSuperTypesStringStringString() throws Exception {
        try {
            delegate.getSuperTypes("foo", "foo", "foo");
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getSuperTypes("foo", "foo", "foo");
    }

    @Test
    public void testGetSystemFunctions() throws Exception {
        try {
            delegate.getSystemFunctions();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getSystemFunctions();
    }

    @Test
    public void testGetTablePrivilegesStringStringString() throws Exception {
        try {
            delegate.getTablePrivileges("foo", "foo", "foo");
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getTablePrivileges("foo", "foo", "foo");
    }

    @Test
    public void testGetTablesStringStringStringStringArray() throws Exception {
        try {
            delegate.getTables("foo", "foo", "foo", null);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getTables("foo", "foo", "foo", null);
    }

    @Test
    public void testGetTableTypes() throws Exception {
        try {
            delegate.getTableTypes();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getTableTypes();
    }

    @Test
    public void testGetTimeDateFunctions() throws Exception {
        try {
            delegate.getTimeDateFunctions();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getTimeDateFunctions();
    }

    @Test
    public void testGetTypeInfo() throws Exception {
        try {
            delegate.getTypeInfo();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getTypeInfo();
    }

    @Test
    public void testGetUDTsStringStringStringIntegerArray() throws Exception {
        try {
            delegate.getUDTs("foo", "foo", "foo", null);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getUDTs("foo", "foo", "foo", null);
    }

    @Test
    public void testGetURL() throws Exception {
        try {
            delegate.getURL();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getURL();
    }

    @Test
    public void testGetUserName() throws Exception {
        try {
            delegate.getUserName();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getUserName();
    }

    @Test
    public void testGetVersionColumnsStringStringString() throws Exception {
        try {
            delegate.getVersionColumns("foo", "foo", "foo");
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).getVersionColumns("foo", "foo", "foo");
    }

    @Test
    public void testInsertsAreDetectedInteger() throws Exception {
        try {
            delegate.insertsAreDetected(1);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).insertsAreDetected(1);
    }

    @Test
    public void testIsCatalogAtStart() throws Exception {
        try {
            delegate.isCatalogAtStart();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).isCatalogAtStart();
    }

    @Test
    public void testIsReadOnly() throws Exception {
        try {
            delegate.isReadOnly();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).isReadOnly();
    }

    @Test
    public void testLocatorsUpdateCopy() throws Exception {
        try {
            delegate.locatorsUpdateCopy();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).locatorsUpdateCopy();
    }

    @Test
    public void testNullPlusNonNullIsNull() throws Exception {
        try {
            delegate.nullPlusNonNullIsNull();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).nullPlusNonNullIsNull();
    }

    @Test
    public void testNullsAreSortedAtEnd() throws Exception {
        try {
            delegate.nullsAreSortedAtEnd();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).nullsAreSortedAtEnd();
    }

    @Test
    public void testNullsAreSortedAtStart() throws Exception {
        try {
            delegate.nullsAreSortedAtStart();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).nullsAreSortedAtStart();
    }

    @Test
    public void testNullsAreSortedHigh() throws Exception {
        try {
            delegate.nullsAreSortedHigh();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).nullsAreSortedHigh();
    }

    @Test
    public void testNullsAreSortedLow() throws Exception {
        try {
            delegate.nullsAreSortedLow();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).nullsAreSortedLow();
    }

    @Test
    public void testOthersDeletesAreVisibleInteger() throws Exception {
        try {
            delegate.othersDeletesAreVisible(1);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).othersDeletesAreVisible(1);
    }

    @Test
    public void testOthersInsertsAreVisibleInteger() throws Exception {
        try {
            delegate.othersInsertsAreVisible(1);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).othersInsertsAreVisible(1);
    }

    @Test
    public void testOthersUpdatesAreVisibleInteger() throws Exception {
        try {
            delegate.othersUpdatesAreVisible(1);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).othersUpdatesAreVisible(1);
    }

    @Test
    public void testOwnDeletesAreVisibleInteger() throws Exception {
        try {
            delegate.ownDeletesAreVisible(1);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).ownDeletesAreVisible(1);
    }

    @Test
    public void testOwnInsertsAreVisibleInteger() throws Exception {
        try {
            delegate.ownInsertsAreVisible(1);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).ownInsertsAreVisible(1);
    }

    @Test
    public void testOwnUpdatesAreVisibleInteger() throws Exception {
        try {
            delegate.ownUpdatesAreVisible(1);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).ownUpdatesAreVisible(1);
    }

    @Test
    public void testStoresLowerCaseIdentifiers() throws Exception {
        try {
            delegate.storesLowerCaseIdentifiers();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).storesLowerCaseIdentifiers();
    }

    @Test
    public void testStoresLowerCaseQuotedIdentifiers() throws Exception {
        try {
            delegate.storesLowerCaseQuotedIdentifiers();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).storesLowerCaseQuotedIdentifiers();
    }

    @Test
    public void testStoresMixedCaseIdentifiers() throws Exception {
        try {
            delegate.storesMixedCaseIdentifiers();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).storesMixedCaseIdentifiers();
    }

    @Test
    public void testStoresMixedCaseQuotedIdentifiers() throws Exception {
        try {
            delegate.storesMixedCaseQuotedIdentifiers();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).storesMixedCaseQuotedIdentifiers();
    }

    @Test
    public void testStoresUpperCaseIdentifiers() throws Exception {
        try {
            delegate.storesUpperCaseIdentifiers();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).storesUpperCaseIdentifiers();
    }

    @Test
    public void testStoresUpperCaseQuotedIdentifiers() throws Exception {
        try {
            delegate.storesUpperCaseQuotedIdentifiers();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).storesUpperCaseQuotedIdentifiers();
    }

    @Test
    public void testSupportsAlterTableWithAddColumn() throws Exception {
        try {
            delegate.supportsAlterTableWithAddColumn();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsAlterTableWithAddColumn();
    }

    @Test
    public void testSupportsAlterTableWithDropColumn() throws Exception {
        try {
            delegate.supportsAlterTableWithDropColumn();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsAlterTableWithDropColumn();
    }

    @Test
    public void testSupportsANSI92EntryLevelSQL() throws Exception {
        try {
            delegate.supportsANSI92EntryLevelSQL();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsANSI92EntryLevelSQL();
    }

    @Test
    public void testSupportsANSI92FullSQL() throws Exception {
        try {
            delegate.supportsANSI92FullSQL();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsANSI92FullSQL();
    }

    @Test
    public void testSupportsANSI92IntermediateSQL() throws Exception {
        try {
            delegate.supportsANSI92IntermediateSQL();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsANSI92IntermediateSQL();
    }

    @Test
    public void testSupportsBatchUpdates() throws Exception {
        try {
            delegate.supportsBatchUpdates();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsBatchUpdates();
    }

    @Test
    public void testSupportsCatalogsInDataManipulation() throws Exception {
        try {
            delegate.supportsCatalogsInDataManipulation();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsCatalogsInDataManipulation();
    }

    @Test
    public void testSupportsCatalogsInIndexDefinitions() throws Exception {
        try {
            delegate.supportsCatalogsInIndexDefinitions();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsCatalogsInIndexDefinitions();
    }

    @Test
    public void testSupportsCatalogsInPrivilegeDefinitions() throws Exception {
        try {
            delegate.supportsCatalogsInPrivilegeDefinitions();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsCatalogsInPrivilegeDefinitions();
    }

    @Test
    public void testSupportsCatalogsInProcedureCalls() throws Exception {
        try {
            delegate.supportsCatalogsInProcedureCalls();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsCatalogsInProcedureCalls();
    }

    @Test
    public void testSupportsCatalogsInTableDefinitions() throws Exception {
        try {
            delegate.supportsCatalogsInTableDefinitions();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsCatalogsInTableDefinitions();
    }

    @Test
    public void testSupportsColumnAliasing() throws Exception {
        try {
            delegate.supportsColumnAliasing();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsColumnAliasing();
    }

    @Test
    public void testSupportsConvert() throws Exception {
        try {
            delegate.supportsConvert();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsConvert();
    }

    @Test
    public void testSupportsConvertIntegerInteger() throws Exception {
        try {
            delegate.supportsConvert(1, 1);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsConvert(1, 1);
    }

    @Test
    public void testSupportsCoreSQLGrammar() throws Exception {
        try {
            delegate.supportsCoreSQLGrammar();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsCoreSQLGrammar();
    }

    @Test
    public void testSupportsCorrelatedSubqueries() throws Exception {
        try {
            delegate.supportsCorrelatedSubqueries();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsCorrelatedSubqueries();
    }

    @Test
    public void testSupportsDataDefinitionAndDataManipulationTransactions() throws Exception {
        try {
            delegate.supportsDataDefinitionAndDataManipulationTransactions();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsDataDefinitionAndDataManipulationTransactions();
    }

    @Test
    public void testSupportsDataManipulationTransactionsOnly() throws Exception {
        try {
            delegate.supportsDataManipulationTransactionsOnly();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsDataManipulationTransactionsOnly();
    }

    @Test
    public void testSupportsDifferentTableCorrelationNames() throws Exception {
        try {
            delegate.supportsDifferentTableCorrelationNames();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsDifferentTableCorrelationNames();
    }

    @Test
    public void testSupportsExpressionsInOrderBy() throws Exception {
        try {
            delegate.supportsExpressionsInOrderBy();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsExpressionsInOrderBy();
    }

    @Test
    public void testSupportsExtendedSQLGrammar() throws Exception {
        try {
            delegate.supportsExtendedSQLGrammar();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsExtendedSQLGrammar();
    }

    @Test
    public void testSupportsFullOuterJoins() throws Exception {
        try {
            delegate.supportsFullOuterJoins();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsFullOuterJoins();
    }

    @Test
    public void testSupportsGetGeneratedKeys() throws Exception {
        try {
            delegate.supportsGetGeneratedKeys();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsGetGeneratedKeys();
    }

    @Test
    public void testSupportsGroupBy() throws Exception {
        try {
            delegate.supportsGroupBy();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsGroupBy();
    }

    @Test
    public void testSupportsGroupByBeyondSelect() throws Exception {
        try {
            delegate.supportsGroupByBeyondSelect();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsGroupByBeyondSelect();
    }

    @Test
    public void testSupportsGroupByUnrelated() throws Exception {
        try {
            delegate.supportsGroupByUnrelated();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsGroupByUnrelated();
    }

    @Test
    public void testSupportsIntegrityEnhancementFacility() throws Exception {
        try {
            delegate.supportsIntegrityEnhancementFacility();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsIntegrityEnhancementFacility();
    }

    @Test
    public void testSupportsLikeEscapeClause() throws Exception {
        try {
            delegate.supportsLikeEscapeClause();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsLikeEscapeClause();
    }

    @Test
    public void testSupportsLimitedOuterJoins() throws Exception {
        try {
            delegate.supportsLimitedOuterJoins();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsLimitedOuterJoins();
    }

    @Test
    public void testSupportsMinimumSQLGrammar() throws Exception {
        try {
            delegate.supportsMinimumSQLGrammar();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsMinimumSQLGrammar();
    }

    @Test
    public void testSupportsMixedCaseIdentifiers() throws Exception {
        try {
            delegate.supportsMixedCaseIdentifiers();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsMixedCaseIdentifiers();
    }

    @Test
    public void testSupportsMixedCaseQuotedIdentifiers() throws Exception {
        try {
            delegate.supportsMixedCaseQuotedIdentifiers();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsMixedCaseQuotedIdentifiers();
    }

    @Test
    public void testSupportsMultipleOpenResults() throws Exception {
        try {
            delegate.supportsMultipleOpenResults();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsMultipleOpenResults();
    }

    @Test
    public void testSupportsMultipleResultSets() throws Exception {
        try {
            delegate.supportsMultipleResultSets();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsMultipleResultSets();
    }

    @Test
    public void testSupportsMultipleTransactions() throws Exception {
        try {
            delegate.supportsMultipleTransactions();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsMultipleTransactions();
    }

    @Test
    public void testSupportsNamedParameters() throws Exception {
        try {
            delegate.supportsNamedParameters();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsNamedParameters();
    }

    @Test
    public void testSupportsNonNullableColumns() throws Exception {
        try {
            delegate.supportsNonNullableColumns();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsNonNullableColumns();
    }

    @Test
    public void testSupportsOpenCursorsAcrossCommit() throws Exception {
        try {
            delegate.supportsOpenCursorsAcrossCommit();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsOpenCursorsAcrossCommit();
    }

    @Test
    public void testSupportsOpenCursorsAcrossRollback() throws Exception {
        try {
            delegate.supportsOpenCursorsAcrossRollback();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsOpenCursorsAcrossRollback();
    }

    @Test
    public void testSupportsOpenStatementsAcrossCommit() throws Exception {
        try {
            delegate.supportsOpenStatementsAcrossCommit();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsOpenStatementsAcrossCommit();
    }

    @Test
    public void testSupportsOpenStatementsAcrossRollback() throws Exception {
        try {
            delegate.supportsOpenStatementsAcrossRollback();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsOpenStatementsAcrossRollback();
    }

    @Test
    public void testSupportsOrderByUnrelated() throws Exception {
        try {
            delegate.supportsOrderByUnrelated();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsOrderByUnrelated();
    }

    @Test
    public void testSupportsOuterJoins() throws Exception {
        try {
            delegate.supportsOuterJoins();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsOuterJoins();
    }

    @Test
    public void testSupportsPositionedDelete() throws Exception {
        try {
            delegate.supportsPositionedDelete();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsPositionedDelete();
    }

    @Test
    public void testSupportsPositionedUpdate() throws Exception {
        try {
            delegate.supportsPositionedUpdate();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsPositionedUpdate();
    }

    @Test
    public void testSupportsRefCursors() throws Exception {
        try {
            delegate.supportsRefCursors();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsRefCursors();
    }

    @Test
    public void testSupportsResultSetConcurrencyIntegerInteger() throws Exception {
        try {
            delegate.supportsResultSetConcurrency(1, 1);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsResultSetConcurrency(1, 1);
    }

    @Test
    public void testSupportsResultSetHoldabilityInteger() throws Exception {
        try {
            delegate.supportsResultSetHoldability(1);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsResultSetHoldability(1);
    }

    @Test
    public void testSupportsResultSetTypeInteger() throws Exception {
        try {
            delegate.supportsResultSetType(1);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsResultSetType(1);
    }

    @Test
    public void testSupportsSavepoints() throws Exception {
        try {
            delegate.supportsSavepoints();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsSavepoints();
    }

    @Test
    public void testSupportsSchemasInDataManipulation() throws Exception {
        try {
            delegate.supportsSchemasInDataManipulation();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsSchemasInDataManipulation();
    }

    @Test
    public void testSupportsSchemasInIndexDefinitions() throws Exception {
        try {
            delegate.supportsSchemasInIndexDefinitions();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsSchemasInIndexDefinitions();
    }

    @Test
    public void testSupportsSchemasInPrivilegeDefinitions() throws Exception {
        try {
            delegate.supportsSchemasInPrivilegeDefinitions();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsSchemasInPrivilegeDefinitions();
    }

    @Test
    public void testSupportsSchemasInProcedureCalls() throws Exception {
        try {
            delegate.supportsSchemasInProcedureCalls();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsSchemasInProcedureCalls();
    }

    @Test
    public void testSupportsSchemasInTableDefinitions() throws Exception {
        try {
            delegate.supportsSchemasInTableDefinitions();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsSchemasInTableDefinitions();
    }

    @Test
    public void testSupportsSelectForUpdate() throws Exception {
        try {
            delegate.supportsSelectForUpdate();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsSelectForUpdate();
    }

    @Test
    public void testSupportsStatementPooling() throws Exception {
        try {
            delegate.supportsStatementPooling();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsStatementPooling();
    }

    @Test
    public void testSupportsStoredFunctionsUsingCallSyntax() throws Exception {
        try {
            delegate.supportsStoredFunctionsUsingCallSyntax();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsStoredFunctionsUsingCallSyntax();
    }

    @Test
    public void testSupportsStoredProcedures() throws Exception {
        try {
            delegate.supportsStoredProcedures();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsStoredProcedures();
    }

    @Test
    public void testSupportsSubqueriesInComparisons() throws Exception {
        try {
            delegate.supportsSubqueriesInComparisons();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsSubqueriesInComparisons();
    }

    @Test
    public void testSupportsSubqueriesInExists() throws Exception {
        try {
            delegate.supportsSubqueriesInExists();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsSubqueriesInExists();
    }

    @Test
    public void testSupportsSubqueriesInIns() throws Exception {
        try {
            delegate.supportsSubqueriesInIns();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsSubqueriesInIns();
    }

    @Test
    public void testSupportsSubqueriesInQuantifieds() throws Exception {
        try {
            delegate.supportsSubqueriesInQuantifieds();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsSubqueriesInQuantifieds();
    }

    @Test
    public void testSupportsTableCorrelationNames() throws Exception {
        try {
            delegate.supportsTableCorrelationNames();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsTableCorrelationNames();
    }

    @Test
    public void testSupportsTransactionIsolationLevelInteger() throws Exception {
        try {
            delegate.supportsTransactionIsolationLevel(1);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsTransactionIsolationLevel(1);
    }

    @Test
    public void testSupportsTransactions() throws Exception {
        try {
            delegate.supportsTransactions();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsTransactions();
    }

    @Test
    public void testSupportsUnion() throws Exception {
        try {
            delegate.supportsUnion();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsUnion();
    }

    @Test
    public void testSupportsUnionAll() throws Exception {
        try {
            delegate.supportsUnionAll();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).supportsUnionAll();
    }

    @Test
    public void testUpdatesAreDetectedInteger() throws Exception {
        try {
            delegate.updatesAreDetected(1);
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).updatesAreDetected(1);
    }

    @Test
    public void testUsesLocalFilePerTable() throws Exception {
        try {
            delegate.usesLocalFilePerTable();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).usesLocalFilePerTable();
    }

    @Test
    public void testUsesLocalFiles() throws Exception {
        try {
            delegate.usesLocalFiles();
        } catch (final SQLException e) {
        }
        verify(obj, times(1)).usesLocalFiles();
    }

    @Test
    public void testWrap() throws SQLException {
        assertEquals(delegate, delegate.unwrap(DatabaseMetaData.class));
        assertEquals(delegate, delegate.unwrap(DelegatingDatabaseMetaData.class));
        assertEquals(obj, delegate.unwrap(obj.getClass()));
        assertNull(delegate.unwrap(String.class));
        assertTrue(delegate.isWrapperFor(DatabaseMetaData.class));
        assertTrue(delegate.isWrapperFor(DelegatingDatabaseMetaData.class));
        assertTrue(delegate.isWrapperFor(obj.getClass()));
        assertFalse(delegate.isWrapperFor(String.class));
    }
}
