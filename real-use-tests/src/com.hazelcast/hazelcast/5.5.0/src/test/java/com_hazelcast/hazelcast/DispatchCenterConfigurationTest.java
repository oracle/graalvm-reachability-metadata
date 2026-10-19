/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_hazelcast.hazelcast;

import com.hazelcast.cluster.MembershipEvent;
import com.hazelcast.cluster.MembershipListener;
import com.hazelcast.collection.IQueue;
import com.hazelcast.collection.ItemEvent;
import com.hazelcast.collection.ItemListener;
import com.hazelcast.config.Config;
import com.hazelcast.config.InMemoryFormat;
import com.hazelcast.config.InMemoryXmlConfig;
import com.hazelcast.config.InMemoryYamlConfig;
import com.hazelcast.config.InvalidConfigurationException;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.QueueConfig;
import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.map.listener.EntryAddedListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises declarative and dynamic member configuration: YAML and XML configs with variable
 * substitution, wildcard data-structure configs, listeners declared by class name, schema
 * validation, and configs added to a running cluster.
 *
 * A parcel dispatch center runs two members, one configured from YAML and one from the
 * equivalent XML, as an operator would ship them. Order maps match a wildcard config, the
 * dispatch queue is bounded by a substituted capacity, and Hazelcast instantiates the declared
 * listeners itself. At runtime the operator adds configs for new structures and sees them reach
 * the other member, while conflicting or malformed configs are rejected.
 */
class DispatchCenterConfigurationTest {

    private static final String YAML = """
            hazelcast:
              cluster-name: ${cluster.name}
              properties:
                hazelcast.phone.home.enabled: false
              network:
                port:
                  auto-increment: true
                  port: 5701
                join:
                  multicast:
                    enabled: false
                  auto-detection:
                    enabled: false
                  tcp-ip:
                    enabled: true
                    member-list:
                      - 127.0.0.1
                interfaces:
                  enabled: true
                  interfaces:
                    - 127.0.0.1
              listeners:
                - com_hazelcast.hazelcast.DispatchCenterConfigurationTest$JoinRecorder
              map:
                orders-*:
                  backup-count: 1
                  read-backup-data: true
                  in-memory-format: OBJECT
                  entry-listeners:
                    - class-name: com_hazelcast.hazelcast.DispatchCenterConfigurationTest$OrderRecorder
                      include-value: true
                      local: false
              queue:
                dispatch:
                  max-size: ${dispatch.capacity}
                  backup-count: 1
                  item-listeners:
                    - class-name: com_hazelcast.hazelcast.DispatchCenterConfigurationTest$ParcelRecorder
                      include-value: true
            """;

    private static final String XML = """
            <hazelcast xmlns="http://www.hazelcast.com/schema/config"
                       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                       xsi:schemaLocation="http://www.hazelcast.com/schema/config
                       http://www.hazelcast.com/schema/config/hazelcast-config-5.5.xsd">
                <cluster-name>${cluster.name}</cluster-name>
                <properties>
                    <property name="hazelcast.phone.home.enabled">false</property>
                </properties>
                <network>
                    <port auto-increment="true">5701</port>
                    <join>
                        <multicast enabled="false"/>
                        <auto-detection enabled="false"/>
                        <tcp-ip enabled="true">
                            <member>127.0.0.1</member>
                        </tcp-ip>
                    </join>
                    <interfaces enabled="true">
                        <interface>127.0.0.1</interface>
                    </interfaces>
                </network>
                <map name="orders-*">
                    <in-memory-format>OBJECT</in-memory-format>
                    <backup-count>1</backup-count>
                    <read-backup-data>true</read-backup-data>
                    <entry-listeners>
                        <entry-listener include-value="true" local="false">\
            com_hazelcast.hazelcast.DispatchCenterConfigurationTest$OrderRecorder</entry-listener>
                    </entry-listeners>
                </map>
                <queue name="dispatch">
                    <item-listeners>
                        <item-listener include-value="true">\
            com_hazelcast.hazelcast.DispatchCenterConfigurationTest$ParcelRecorder</item-listener>
                    </item-listeners>
                    <max-size>${dispatch.capacity}</max-size>
                    <backup-count>1</backup-count>
                </queue>
            </hazelcast>
            """;

    private static final Set<UUID> JOINED_MEMBERS = ConcurrentHashMap.newKeySet();

    private static final Set<String> RECORDED_ORDERS = ConcurrentHashMap.newKeySet();

    private static final Set<String> RECORDED_PARCELS = ConcurrentHashMap.newKeySet();

    private final String clusterName = "dispatch-" + UUID.randomUUID();

    private HazelcastInstance yamlMember;

    private HazelcastInstance xmlMember;

    @BeforeEach
    void startDispatchCenter() throws InterruptedException {
        JOINED_MEMBERS.clear();
        RECORDED_ORDERS.clear();
        RECORDED_PARCELS.clear();

        Properties variables = new Properties();
        variables.setProperty("cluster.name", clusterName);
        variables.setProperty("dispatch.capacity", "3");

        yamlMember = Hazelcast.newHazelcastInstance(new InMemoryYamlConfig(YAML, variables));
        xmlMember = Hazelcast.newHazelcastInstance(new InMemoryXmlConfig(XML, variables));
        awaitCondition(() -> yamlMember.getCluster().getMembers().size() == 2
                && xmlMember.getCluster().getMembers().size() == 2);
    }

    @AfterEach
    void stopDispatchCenter() {
        if (xmlMember != null) {
            xmlMember.shutdown();
        }
        if (yamlMember != null) {
            yamlMember.shutdown();
        }
    }

    @Test
    void yamlAndXmlMembersShareTheSubstitutedAndWildcardSettings() throws InterruptedException {
        for (HazelcastInstance member : new HazelcastInstance[] {yamlMember, xmlMember}) {
            Config config = member.getConfig();
            assertThat(config.getClusterName()).isEqualTo(clusterName);

            MapConfig orders = config.findMapConfig("orders-eu");
            assertThat(orders.getName()).isEqualTo("orders-*");
            assertThat(orders.getBackupCount()).isEqualTo(1);
            assertThat(orders.isReadBackupData()).isTrue();
            assertThat(orders.getInMemoryFormat()).isEqualTo(InMemoryFormat.OBJECT);
            assertThat(orders.getEntryListenerConfigs()).singleElement()
                    .satisfies(listener -> assertThat(listener.getClassName()).endsWith("$OrderRecorder"));

            MapConfig invoices = config.findMapConfig("invoices");
            assertThat(invoices.getInMemoryFormat()).isEqualTo(InMemoryFormat.BINARY);
            assertThat(invoices.getEntryListenerConfigs()).isEmpty();

            QueueConfig dispatch = config.findQueueConfig("dispatch");
            assertThat(dispatch.getMaxSize()).isEqualTo(3);
            assertThat(dispatch.getItemListenerConfigs()).hasSize(1);
        }

        UUID xmlMemberId = xmlMember.getCluster().getLocalMember().getUuid();
        awaitCondition(() -> JOINED_MEMBERS.contains(xmlMemberId));
    }

    @Test
    void listenersDeclaredByClassNameObserveMatchingStructures() throws InterruptedException {
        IMap<String, String> euOrders = xmlMember.getMap("orders-eu");
        IMap<String, String> usOrders = yamlMember.getMap("orders-us");
        IMap<String, String> invoices = yamlMember.getMap("invoices");
        euOrders.put("eu-1", "Berlin");
        usOrders.put("us-1", "Boston");
        invoices.put("inv-1", "paid");

        awaitCondition(() -> RECORDED_ORDERS.containsAll(Set.of("orders-eu:eu-1=Berlin", "orders-us:us-1=Boston")));
        assertThat(RECORDED_ORDERS).containsExactlyInAnyOrder("orders-eu:eu-1=Berlin", "orders-us:us-1=Boston");

        IQueue<String> dispatch = yamlMember.getQueue("dispatch");
        assertThat(dispatch.offer("parcel-1")).isTrue();
        assertThat(dispatch.offer("parcel-2")).isTrue();
        assertThat(dispatch.offer("parcel-3")).isTrue();
        assertThat(dispatch.offer("parcel-4")).isFalse();
        assertThat(dispatch.remainingCapacity()).isZero();

        IQueue<String> sameDispatch = xmlMember.getQueue("dispatch");
        assertThat(sameDispatch.poll()).isEqualTo("parcel-1");
        assertThat(sameDispatch.offer("parcel-4")).isTrue();

        awaitCondition(() -> RECORDED_PARCELS.size() == 4);
        assertThat(RECORDED_PARCELS).containsExactlyInAnyOrder("parcel-1", "parcel-2", "parcel-3", "parcel-4");
    }

    @Test
    void configsAddedAtRuntimeReachEveryMemberAndConflictsAreRejected() {
        yamlMember.getConfig().addMapConfig(new MapConfig("audit-*")
                .setBackupCount(0)
                .setInMemoryFormat(InMemoryFormat.OBJECT));
        xmlMember.getConfig().addQueueConfig(new QueueConfig("returns").setMaxSize(1));

        MapConfig audit = xmlMember.getConfig().findMapConfig("audit-2026");
        assertThat(audit.getName()).isEqualTo("audit-*");
        assertThat(audit.getBackupCount()).isZero();
        assertThat(audit.getInMemoryFormat()).isEqualTo(InMemoryFormat.OBJECT);

        IQueue<String> returns = yamlMember.getQueue("returns");
        assertThat(returns.offer("parcel-9")).isTrue();
        assertThat(returns.offer("parcel-10")).isFalse();

        yamlMember.getConfig().addMapConfig(new MapConfig("audit-*")
                .setBackupCount(0)
                .setInMemoryFormat(InMemoryFormat.OBJECT));
        assertThatThrownBy(() -> xmlMember.getConfig().addMapConfig(new MapConfig("audit-*").setBackupCount(2)))
                .isInstanceOf(InvalidConfigurationException.class);
        assertThatThrownBy(() -> yamlMember.getConfig().addMapConfig(new MapConfig("orders-*").setBackupCount(0)))
                .isInstanceOf(InvalidConfigurationException.class);
        assertThat(yamlMember.getConfig().findMapConfig("orders-eu").getBackupCount()).isEqualTo(1);
    }

    @Test
    void malformedDeclarativeConfigsAreRejectedBeforeStartup() {
        assertThatThrownBy(() -> new InMemoryYamlConfig("""
                hazelcast:
                  map:
                    orders:
                      backup-count: seven
                """))
                .isInstanceOf(InvalidConfigurationException.class);
        assertThatThrownBy(() -> new InMemoryXmlConfig("""
                <hazelcast xmlns="http://www.hazelcast.com/schema/config">
                    <queue name="dispatch">
                        <max-capacity>3</max-capacity>
                    </queue>
                </hazelcast>
                """))
                .isInstanceOf(InvalidConfigurationException.class);
    }

    private static void awaitCondition(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (!condition.getAsBoolean()) {
            assertThat(System.nanoTime()).as("condition reached within 60 seconds").isLessThan(deadline);
            Thread.sleep(50);
        }
    }

    /** Membership listener the YAML member instantiates from its declared class name. */
    public static final class JoinRecorder implements MembershipListener {

        @Override
        public void memberAdded(MembershipEvent event) {
            JOINED_MEMBERS.add(event.getMember().getUuid());
        }

        @Override
        public void memberRemoved(MembershipEvent event) {
        }
    }

    /** Entry listener every member attaches to maps matching the order wildcard. */
    public static final class OrderRecorder implements EntryAddedListener<String, String> {

        @Override
        public void entryAdded(EntryEvent<String, String> event) {
            RECORDED_ORDERS.add(event.getName() + ":" + event.getKey() + "=" + event.getValue());
        }
    }

    /** Item listener every member attaches to the dispatch queue. */
    public static final class ParcelRecorder implements ItemListener<String> {

        @Override
        public void itemAdded(ItemEvent<String> event) {
            RECORDED_PARCELS.add(event.getItem());
        }

        @Override
        public void itemRemoved(ItemEvent<String> event) {
        }
    }
}
