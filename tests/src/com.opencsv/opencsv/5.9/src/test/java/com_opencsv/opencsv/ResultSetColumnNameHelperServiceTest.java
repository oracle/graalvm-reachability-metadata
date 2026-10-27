/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.ResultSetColumnNameHelperService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ResultSetColumnNameHelperServiceTest {

    @Test
    void setColumnNamesRejectsMismatchedHeaderCount() {
        ResultSetColumnNameHelperService helperService = helperService();

        assertThatThrownBy(() -> helperService.setColumnNames(
                new String[] {"id", "name"},
                new String[] {"Identifier"}))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("number of column names");
    }

    @Test
    void setColumnNamesRejectsBlankColumnNames() {
        ResultSetColumnNameHelperService helperService = helperService();

        assertThatThrownBy(() -> helperService.setColumnNames(
                new String[] {"id", " "},
                new String[] {"Identifier", "Name"}))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Column names cannot be null");
    }

    @Test
    void setColumnNamesRejectsBlankColumnHeaders() {
        ResultSetColumnNameHelperService helperService = helperService();

        assertThatThrownBy(() -> helperService.setColumnNames(
                new String[] {"id", "name"},
                new String[] {"Identifier", ""}))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Column header names cannot be null");
    }

    @Test
    void getColumnNamesRejectsConfiguredColumnMissingFromResultSet() throws SQLException {
        ResultSetColumnNameHelperService helperService = helperService();
        helperService.setColumnNames(new String[] {"missing"}, new String[] {"Missing"});

        assertThatThrownBy(() -> helperService.getColumnNames(resultSetWithColumns("id", "name")))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("missing")
                .hasMessageContaining("does not exist");
    }

    @Test
    void getColumnNamesReturnsConfiguredHeadersForExistingColumns() throws SQLException {
        ResultSetColumnNameHelperService helperService = helperService();
        helperService.setColumnNames(new String[] {"name", "id"}, new String[] {"Name", "Identifier"});

        String[] columnNames = helperService.getColumnNames(resultSetWithColumns("id", "name", "ignored"));

        assertThat(columnNames).containsExactly("Name", "Identifier");
    }

    private static ResultSetColumnNameHelperService helperService() {
        ResultSetColumnNameHelperService helperService = new ResultSetColumnNameHelperService();
        helperService.setErrorLocale(Locale.US);
        return helperService;
    }

    private static ResultSet resultSetWithColumns(String... columnLabels) {
        InvocationHandler invocationHandler = new ResultSetInvocationHandler(columnLabels);
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[] {ResultSet.class},
                invocationHandler);
    }

    private static ResultSetMetaData resultSetMetaDataWithColumns(String[] columnLabels) {
        InvocationHandler invocationHandler = new ResultSetMetaDataInvocationHandler(columnLabels);
        return (ResultSetMetaData) Proxy.newProxyInstance(
                ResultSetMetaData.class.getClassLoader(),
                new Class<?>[] {ResultSetMetaData.class},
                invocationHandler);
    }

    private static final class ResultSetInvocationHandler implements InvocationHandler {
        private final ResultSetMetaData metadata;

        private ResultSetInvocationHandler(String[] columnLabels) {
            metadata = resultSetMetaDataWithColumns(columnLabels);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getDeclaringClass() == Object.class) {
                return invokeObjectMethod(proxy, method, args);
            }
            if ("getMetaData".equals(method.getName())) {
                return metadata;
            }
            throw new UnsupportedOperationException("Unexpected ResultSet method: " + method.getName());
        }
    }

    private static final class ResultSetMetaDataInvocationHandler implements InvocationHandler {
        private final String[] columnLabels;

        private ResultSetMetaDataInvocationHandler(String[] columnLabels) {
            this.columnLabels = columnLabels.clone();
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getDeclaringClass() == Object.class) {
                return invokeObjectMethod(proxy, method, args);
            }
            if ("getColumnCount".equals(method.getName())) {
                return columnLabels.length;
            }
            if ("getColumnLabel".equals(method.getName())) {
                return columnLabels[(Integer) args[0] - 1];
            }
            throw new UnsupportedOperationException("Unexpected ResultSetMetaData method: " + method.getName());
        }
    }

    private static Object invokeObjectMethod(Object proxy, Method method, Object[] args) {
        if ("toString".equals(method.getName())) {
            return proxy.getClass().getInterfaces()[0].getSimpleName() + " test proxy";
        }
        if ("hashCode".equals(method.getName())) {
            return System.identityHashCode(proxy);
        }
        if ("equals".equals(method.getName())) {
            return proxy == args[0];
        }
        throw new UnsupportedOperationException("Unexpected Object method: " + method.getName());
    }
}
