/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import java.util.List;
import java.util.Map;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;
import javax.management.StandardMBean;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;
import javax.management.openmbean.TabularType;

import org.junit.jupiter.api.Test;
import org.springframework.jmx.access.MBeanProxyFactoryBean;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MBeanClientInterceptorTest {
    @Test
    void convertsOpenDataResultsToClientValuesArraysAndCollections() throws Exception {
        MBeanServer server = MBeanServerFactory.newMBeanServer();
        ObjectName name = new ObjectName("forge:type=Inventory");
        InventoryData data = new InventoryData();
        server.registerMBean(new StandardMBean(data, InventoryDataMBean.class), name);
        MBeanProxyFactoryBean factory = new MBeanProxyFactoryBean();
        try {
            factory.setServer(server);
            factory.setObjectName(name);
            factory.setUseStrictCasing(false);
            factory.setProxyInterface(InventoryView.class);
            factory.afterPropertiesSet();
            InventoryView view = (InventoryView) factory.getObject();
            Snapshot expected = new Snapshot("warehouse");
            assertEquals(expected, view.snapshot());
            assertArrayEquals(new Snapshot[]{expected}, view.snapshots());
            assertEquals(List.of(expected), view.history());
            assertEquals(new TableSnapshot(1), view.table());
            assertArrayEquals(new TableSnapshot[]{new TableSnapshot(1)}, view.tables());
            assertEquals(List.of(new TableSnapshot(1)), view.tableHistory());
        } finally {
            factory.destroy();
            server.unregisterMBean(name);
        }
    }

    public interface InventoryView {
        Snapshot snapshot();
        Snapshot[] snapshots();
        List<Snapshot> history();
        TableSnapshot table();
        TableSnapshot[] tables();
        List<TableSnapshot> tableHistory();
    }

    public record Snapshot(String name) {
        public static Snapshot from(CompositeData data) {
            return new Snapshot((String) data.get("name"));
        }
    }

    public record TableSnapshot(int count) {
        public static TableSnapshot from(TabularData data) {
            return new TableSnapshot(data.size());
        }
    }

    public interface InventoryDataMBean {
        CompositeData snapshot();
        CompositeData[] snapshots();
        CompositeData[] history();
        TabularData table();
        TabularData[] tables();
        TabularData[] tableHistory();
    }

    public static class InventoryData implements InventoryDataMBean {
        private final CompositeData row;
        private final TabularData rows;

        public InventoryData() throws OpenDataException {
            CompositeType rowType = new CompositeType("Warehouse", "Warehouse",
                    new String[]{"name"}, new String[]{"Warehouse name"}, new OpenType<?>[]{SimpleType.STRING});
            row = new CompositeDataSupport(rowType, Map.of("name", "warehouse"));
            TabularDataSupport table = new TabularDataSupport(
                    new TabularType("Warehouses", "Warehouses", rowType, new String[]{"name"}));
            table.put(row);
            rows = table;
        }

        @Override
        public CompositeData snapshot() {
            return row;
        }

        @Override
        public CompositeData[] snapshots() {
            return new CompositeData[]{row};
        }

        @Override
        public CompositeData[] history() {
            return snapshots();
        }

        @Override
        public TabularData table() {
            return rows;
        }

        @Override
        public TabularData[] tables() {
            return new TabularData[]{rows};
        }

        @Override
        public TabularData[] tableHistory() {
            return tables();
        }
    }
}
