/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_hazelcast.hazelcast;

import com.hazelcast.collection.IQueue;
import com.hazelcast.config.Config;
import com.hazelcast.config.InMemoryFormat;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.SerializationConfig;
import com.hazelcast.config.SerializerConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.DataSerializableFactory;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;
import com.hazelcast.nio.serialization.StreamSerializer;
import com.hazelcast.nio.serialization.compact.CompactReader;
import com.hazelcast.nio.serialization.compact.CompactSerializer;
import com.hazelcast.nio.serialization.compact.CompactWriter;
import com.hazelcast.nio.serialization.genericrecord.GenericRecord;
import com.hazelcast.nio.serialization.genericrecord.GenericRecordBuilder;
import com.hazelcast.query.Predicates;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises Hazelcast serialization: Compact serialization with an explicit serializer and with
 * a reflectively serialized record, `IdentifiedDataSerializable` with a registered factory, and
 * a custom `StreamSerializer` registered by type.
 *
 * An embedded member backs a loyalty program. Members of the program are stored in a binary map
 * through a Compact serializer whose address field is a record Hazelcast serializes by
 * reflection; the map is queried on top-level and nested Compact fields, and a member enrolled
 * by a partner system as a schema-only `GenericRecord` is read back as the domain type.
 * Redemption orders travel through a map and a queue as `IdentifiedDataSerializable` objects the
 * application factory recreates, and their reward amounts are written by a custom stream
 * serializer.
 */
class LoyaltyProgramSerializationTest {

    private static final String MEMBERS_MAP = "loyalty-members";

    private static final String ORDERS_MAP = "redemption-orders";

    private static final int FACTORY_ID = 1701;

    private static final int ORDER_CLASS_ID = 1;

    private static final int POINTS_TYPE_ID = 4242;

    private final OrderFactory orderFactory = new OrderFactory();

    private final PointsSerializer pointsSerializer = new PointsSerializer();

    private HazelcastInstance member;

    @BeforeEach
    void startMember() {
        member = Hazelcast.newHazelcastInstance(memberConfig());
    }

    @AfterEach
    void stopMember() {
        if (member != null) {
            member.shutdown();
        }
    }

    @Test
    void compactMembersRoundTripAndAreQueriedOnTopLevelAndNestedFields() {
        IMap<String, Customer> members = member.getMap(MEMBERS_MAP);
        Customer ada = new Customer("C-1", "Ada", "GOLD", 1200, new Address("Zurich", "8001"));
        members.put(ada.id(), ada);
        members.put("C-2", new Customer("C-2", "Brian", "SILVER", 300, new Address("Bern", "3011")));
        members.put("C-3", new Customer("C-3", "Chloe", "GOLD", 950, new Address("Bern", "3012")));
        members.put("C-4", new Customer("C-4", "Dmitri", "BRONZE", 40, null));

        Customer stored = members.get("C-1");
        assertThat(stored).isEqualTo(ada).isNotSameAs(ada);
        assertThat(stored.address()).isEqualTo(new Address("Zurich", "8001"));
        assertThat(members.get("C-4").address()).isNull();

        Collection<Customer> gold = members.values(Predicates.equal("tier", "GOLD"));
        assertThat(gold).extracting(Customer::name).containsExactlyInAnyOrder("Ada", "Chloe");

        Collection<Customer> inBern = members.values(Predicates.equal("address.city", "Bern"));
        assertThat(inBern).extracting(Customer::id).containsExactlyInAnyOrder("C-2", "C-3");

        Collection<String> bigSpenders = members.keySet(Predicates.sql("points >= 900 AND tier = 'GOLD'"));
        assertThat(bigSpenders).containsExactlyInAnyOrder("C-1", "C-3");
    }

    @Test
    void partnerEnrollmentAsGenericRecordIsReadBackAsTheDomainType() {
        GenericRecord partnerAddress = GenericRecordBuilder.compact(Address.class.getName())
                .setString("city", "Geneva")
                .setString("postcode", "1201")
                .build();
        GenericRecord enrollment = GenericRecordBuilder.compact(CustomerSerializer.TYPE_NAME)
                .setString("id", "P-77")
                .setString("name", "Elena")
                .setString("tier", "SILVER")
                .setInt32("points", 500)
                .setGenericRecord("address", partnerAddress)
                .build();

        IMap<String, Object> members = member.getMap(MEMBERS_MAP);
        members.put("P-77", enrollment);

        Object stored = members.get("P-77");
        assertThat(stored).isInstanceOf(Customer.class);
        assertThat(stored).isEqualTo(new Customer("P-77", "Elena", "SILVER", 500, new Address("Geneva", "1201")));
        assertThat(members.keySet(Predicates.equal("address.postcode", "1201"))).containsExactly("P-77");
    }

    @Test
    void redemptionOrdersAreRecreatedByTheFactoryAndCarryCustomSerializedPoints() throws InterruptedException {
        IMap<Long, RedemptionOrder> orders = member.getMap(ORDERS_MAP);
        RedemptionOrder espresso = new RedemptionOrder(10L, "C-1", List.of("espresso", "croissant"),
                new Points(350, "coffee-club"));
        orders.put(espresso.orderId(), espresso);
        int createdBeforeRead = orderFactory.created.get();

        RedemptionOrder storedOrder = orders.get(10L);

        assertThat(storedOrder).isEqualTo(espresso).isNotSameAs(espresso);
        assertThat(storedOrder.reward()).isEqualTo(new Points(350, "coffee-club"));
        assertThat(orderFactory.created.get()).isGreaterThan(createdBeforeRead);
        assertThat(pointsSerializer.written.get()).isPositive();
        assertThat(pointsSerializer.read.get()).isPositive();

        IQueue<RedemptionOrder> fulfilment = member.getQueue("fulfilment");
        RedemptionOrder voucher = new RedemptionOrder(11L, "C-3", List.of("voucher"), new Points(900, "partners"));
        assertThat(fulfilment.offer(storedOrder)).isTrue();
        assertThat(fulfilment.offer(voucher)).isTrue();

        assertThat(fulfilment.poll(30, TimeUnit.SECONDS)).isEqualTo(espresso);
        RedemptionOrder next = fulfilment.poll(30, TimeUnit.SECONDS);
        assertThat(next).isEqualTo(voucher).isNotSameAs(voucher);
        assertThat(next.items()).containsExactly("voucher");
        assertThat(fulfilment).isEmpty();
    }

    private Config memberConfig() {
        Config config = new Config();
        config.setClusterName("loyalty-" + UUID.randomUUID());
        config.setProperty("hazelcast.phone.home.enabled", "false");
        config.getNetworkConfig().setPort(5701).setPortAutoIncrement(true);
        JoinConfig join = config.getNetworkConfig().getJoin();
        join.getMulticastConfig().setEnabled(false);
        join.getAutoDetectionConfig().setEnabled(false);
        join.getTcpIpConfig().setEnabled(true).addMember("127.0.0.1");
        config.getNetworkConfig().getInterfaces().setEnabled(true).addInterface("127.0.0.1");

        config.addMapConfig(new MapConfig(MEMBERS_MAP).setInMemoryFormat(InMemoryFormat.BINARY));
        config.addMapConfig(new MapConfig(ORDERS_MAP).setInMemoryFormat(InMemoryFormat.BINARY));

        SerializationConfig serialization = config.getSerializationConfig();
        serialization.getCompactSerializationConfig()
                .addSerializer(new CustomerSerializer())
                .addClass(Address.class);
        serialization.addDataSerializableFactory(FACTORY_ID, orderFactory);
        serialization.addSerializerConfig(new SerializerConfig()
                .setTypeClass(Points.class)
                .setImplementation(pointsSerializer));
        return config;
    }

    /** A loyalty program member, written by an explicit Compact serializer. */
    record Customer(String id, String name, String tier, int points, Address address) {
    }

    /** A postal address that Hazelcast serializes reflectively as a Compact record. */
    record Address(String city, String postcode) {
    }

    /** Reward points of a partner program, written by the custom stream serializer. */
    record Points(int amount, String program) {
    }

    /** Writes and reads customers under a stable Compact type name. */
    static final class CustomerSerializer implements CompactSerializer<Customer> {

        static final String TYPE_NAME = "customer";

        @Override
        public Customer read(CompactReader reader) {
            return new Customer(
                    reader.readString("id"),
                    reader.readString("name"),
                    reader.readString("tier"),
                    reader.readInt32("points"),
                    reader.readCompact("address"));
        }

        @Override
        public void write(CompactWriter writer, Customer customer) {
            writer.writeString("id", customer.id());
            writer.writeString("name", customer.name());
            writer.writeString("tier", customer.tier());
            writer.writeInt32("points", customer.points());
            writer.writeCompact("address", customer.address());
        }

        @Override
        public String getTypeName() {
            return TYPE_NAME;
        }

        @Override
        public Class<Customer> getCompactClass() {
            return Customer.class;
        }
    }

    /** Writes reward points as an amount followed by the program name. */
    static final class PointsSerializer implements StreamSerializer<Points> {

        private final AtomicInteger written = new AtomicInteger();

        private final AtomicInteger read = new AtomicInteger();

        @Override
        public void write(ObjectDataOutput out, Points points) throws IOException {
            written.incrementAndGet();
            out.writeInt(points.amount());
            out.writeString(points.program());
        }

        @Override
        public Points read(ObjectDataInput in) throws IOException {
            read.incrementAndGet();
            return new Points(in.readInt(), in.readString());
        }

        @Override
        public int getTypeId() {
            return POINTS_TYPE_ID;
        }
    }

    /** Recreates redemption orders from their class id and counts the instances it creates. */
    static final class OrderFactory implements DataSerializableFactory {

        private final AtomicInteger created = new AtomicInteger();

        @Override
        public IdentifiedDataSerializable create(int typeId) {
            if (typeId != ORDER_CLASS_ID) {
                return null;
            }
            created.incrementAndGet();
            return new RedemptionOrder();
        }
    }

    /** A redemption order that serializes itself field by field. */
    static final class RedemptionOrder implements IdentifiedDataSerializable {

        private long orderId;

        private String customerId;

        private List<String> items;

        private Points reward;

        RedemptionOrder() {
        }

        RedemptionOrder(long orderId, String customerId, List<String> items, Points reward) {
            this.orderId = orderId;
            this.customerId = customerId;
            this.items = items;
            this.reward = reward;
        }

        long orderId() {
            return orderId;
        }

        List<String> items() {
            return items;
        }

        Points reward() {
            return reward;
        }

        @Override
        public int getFactoryId() {
            return FACTORY_ID;
        }

        @Override
        public int getClassId() {
            return ORDER_CLASS_ID;
        }

        @Override
        public void writeData(ObjectDataOutput out) throws IOException {
            out.writeLong(orderId);
            out.writeString(customerId);
            out.writeStringArray(items.toArray(new String[0]));
            out.writeObject(reward);
        }

        @Override
        public void readData(ObjectDataInput in) throws IOException {
            orderId = in.readLong();
            customerId = in.readString();
            items = List.of(in.readStringArray());
            reward = in.readObject();
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof RedemptionOrder order)) {
                return false;
            }
            return orderId == order.orderId
                    && customerId.equals(order.customerId)
                    && items.equals(order.items)
                    && reward.equals(order.reward);
        }

        @Override
        public int hashCode() {
            return Objects.hash(orderId, customerId, items, reward);
        }
    }
}
