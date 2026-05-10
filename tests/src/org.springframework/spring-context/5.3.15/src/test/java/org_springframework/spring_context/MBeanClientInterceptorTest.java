/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;
import javax.management.StandardMBean;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;
import javax.management.openmbean.TabularType;

import org.junit.jupiter.api.Test;

import org.springframework.jmx.access.MBeanProxyFactoryBean;

public class MBeanClientInterceptorTest {

    @Test
    void convertsOpenMBeanDataToDeclaredProxyReturnTypes() throws Exception {
        final MBeanServer server = MBeanServerFactory.createMBeanServer();
        final ObjectName objectName = new ObjectName("org_springframework.spring_context:type=OpenDataOperations");
        server.registerMBean(new StandardMBean(new OpenDataOperations(), OpenDataOperationsMBean.class), objectName);

        try {
            final MBeanProxyFactoryBean factoryBean = new MBeanProxyFactoryBean();
            factoryBean.setServer(server);
            factoryBean.setObjectName(objectName);
            factoryBean.setProxyInterface(ClientOpenDataOperations.class);
            factoryBean.setUseStrictCasing(false);
            factoryBean.afterPropertiesSet();

            final ClientOpenDataOperations proxy = (ClientOpenDataOperations) factoryBean.getObject();

            assertEquals(new CompositeRecord("composite-one"), proxy.singleComposite());
            assertArrayEquals(new CompositeRecord[] {
                    new CompositeRecord("composite-array-one"),
                    new CompositeRecord("composite-array-two")
            }, proxy.compositeArray());
            assertEquals(asList(
                    new CompositeRecord("composite-list-one"),
                    new CompositeRecord("composite-list-two")), proxy.compositeList());
            assertEquals(new TableRecord(asList("table-one", "table-two")), proxy.singleTable());
        } finally {
            server.unregisterMBean(objectName);
            MBeanServerFactory.releaseMBeanServer(server);
        }
    }

    private static <T> List<T> asList(T first, T second) {
        final List<T> values = new ArrayList<T>();
        values.add(first);
        values.add(second);
        return values;
    }

    public interface ClientOpenDataOperations {

        CompositeRecord singleComposite();

        CompositeRecord[] compositeArray();

        List<CompositeRecord> compositeList();

        TableRecord singleTable();
    }

    public interface OpenDataOperationsMBean {

        CompositeData singleComposite();

        CompositeData[] compositeArray();

        CompositeData[] compositeList();

        TabularData singleTable();
    }

    public static class OpenDataOperations implements OpenDataOperationsMBean {

        @Override
        public CompositeData singleComposite() {
            return composite("composite-one");
        }

        @Override
        public CompositeData[] compositeArray() {
            return new CompositeData[] {
                    composite("composite-array-one"),
                    composite("composite-array-two")
            };
        }

        @Override
        public CompositeData[] compositeList() {
            return new CompositeData[] {
                    composite("composite-list-one"),
                    composite("composite-list-two")
            };
        }

        @Override
        public TabularData singleTable() {
            final TabularDataSupport table = new TabularDataSupport(tableType());
            table.put(composite("table-one"));
            table.put(composite("table-two"));
            return table;
        }
    }

    public static final class CompositeRecord {

        private final String name;

        private CompositeRecord(String name) {
            this.name = name;
        }

        public static CompositeRecord from(CompositeData data) {
            return new CompositeRecord((String) data.get("name"));
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof CompositeRecord)) {
                return false;
            }
            final CompositeRecord that = (CompositeRecord) other;
            return this.name.equals(that.name);
        }

        @Override
        public int hashCode() {
            return this.name.hashCode();
        }

        @Override
        public String toString() {
            return "CompositeRecord{name='" + this.name + "'}";
        }
    }

    public static final class TableRecord {

        private final List<String> names;

        private TableRecord(Collection<String> names) {
            this.names = new ArrayList<String>(names);
            Collections.sort(this.names);
        }

        public static TableRecord from(TabularData data) {
            final List<String> names = new ArrayList<String>();
            for (Object value : data.values()) {
                names.add((String) ((CompositeData) value).get("name"));
            }
            return new TableRecord(names);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof TableRecord)) {
                return false;
            }
            final TableRecord that = (TableRecord) other;
            return this.names.equals(that.names);
        }

        @Override
        public int hashCode() {
            return this.names.hashCode();
        }

        @Override
        public String toString() {
            return "TableRecord{names=" + this.names + "}";
        }
    }

    private static CompositeData composite(String name) {
        try {
            return new CompositeDataSupport(
                    compositeType(),
                    new String[] {"name"},
                    new Object[] {name});
        } catch (OpenDataException ex) {
            throw new IllegalStateException("Failed to create composite test data", ex);
        }
    }

    private static CompositeType compositeType() {
        try {
            return new CompositeType(
                    "NameRecord",
                    "A record containing a name",
                    new String[] {"name"},
                    new String[] {"name"},
                    new SimpleType<?>[] {SimpleType.STRING});
        } catch (OpenDataException ex) {
            throw new IllegalStateException("Failed to create composite test type", ex);
        }
    }

    private static TabularType tableType() {
        try {
            return new TabularType(
                    "NameTable",
                    "A table containing name records",
                    compositeType(),
                    new String[] {"name"});
        } catch (OpenDataException ex) {
            throw new IllegalStateException("Failed to create tabular test type", ex);
        }
    }
}
