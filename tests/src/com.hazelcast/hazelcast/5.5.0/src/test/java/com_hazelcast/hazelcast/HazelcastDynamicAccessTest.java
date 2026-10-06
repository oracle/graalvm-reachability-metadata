/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_hazelcast.hazelcast;

import com.hazelcast.config.AttributeConfig;
import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.jet.config.JobConfig;
import com.hazelcast.jet.json.JsonUtil;
import com.hazelcast.map.IMap;
import com.hazelcast.query.extractor.ValueCollector;
import com.hazelcast.query.extractor.ValueExtractor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

import static com.hazelcast.query.Predicates.and;
import static com.hazelcast.query.Predicates.equal;
import static org.assertj.core.api.Assertions.assertThat;

public class HazelcastDynamicAccessTest {
    private static HazelcastInstance instance;

    @BeforeAll
    static void startMember() {
        Config config = new Config();
        config.setClusterName("dynamic-access-test");
        config.getNetworkConfig().setPort(0);
        config.getNetworkConfig().getJoin().getAutoDetectionConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
        config.addMapConfig(new MapConfig("customer-profiles")
                .setBackupCount(0)
                .addAttributeConfig(new AttributeConfig("displayName", DisplayNameExtractor.class.getName())));
        config.getSerializationConfig().getCompactSerializationConfig().addClass(CompactEmployee.class);
        instance = Hazelcast.newHazelcastInstance(config);
    }

    @AfterAll
    static void stopMember() {
        instance.shutdown();
    }

    @Test
    void queriesJavaObjectsThroughReflectiveAttributesAndConfiguredExtractor() {
        IMap<String, CustomerProfile> profiles = instance.getMap("customer-profiles");
        CustomerProfile ada = new CustomerProfile("Ada", 4, new Address("London"));
        CustomerProfile grace = new CustomerProfile("Grace", 5, new Address("New York"));
        profiles.put("ada", ada);
        profiles.put("grace", grace);

        Collection<CustomerProfile> reflected = profiles.values(and(
                equal("name", "Ada"),
                equal("level", 4),
                equal("address.city", "London")));
        Collection<CustomerProfile> extracted = profiles.values(equal("displayName", "Ada#4"));

        assertThat(reflected).extracting(CustomerProfile::getName).containsExactly("Ada");
        assertThat(extracted).extracting(CustomerProfile::getName).containsExactly("Ada");
    }

    @Test
    void serializesJavaRecordWithCompactSerialization() {
        IMap<String, CompactEmployee> employees = instance.getMap("compact-record-employees");
        CompactEmployee expected = new CompactEmployee("Lin", 37);

        employees.put("architect", expected);

        assertThat(employees.get("architect")).isEqualTo(expected);
    }

    @Test
    void convertsBeansAndCollectionsWithJetJsonApi() throws IOException {
        JsonProfile profile = new JsonProfile();
        profile.setName("Katherine");
        profile.setLevel(6);

        String json = JsonUtil.toJson(profile);
        JsonProfile restored = JsonUtil.beanFrom(json, JsonProfile.class);
        Map<String, Object> values = JsonUtil.mapFrom(json);

        assertThat(restored.getName()).isEqualTo("Katherine");
        assertThat(restored.getLevel()).isEqualTo(6);
        assertThat(values).containsEntry("name", "Katherine").containsEntry("level", 6);
        assertThat(JsonUtil.listFrom("[1,2,3]")).containsExactly(1, 2, 3);
    }

    @Test
    void addsClassAndNestedClassesToJetJobResources() {
        JobConfig jobConfig = new JobConfig();

        jobConfig.addClass(DeployableTask.class);

        assertThat(jobConfig.getResourceConfigs()).containsKeys(
                DeployableTask.class.getName().replace('.', '/') + ".class",
                DeployableTask.Marker.class.getName().replace('.', '/') + ".class");
    }

    public static final class DisplayNameExtractor implements ValueExtractor<CustomerProfile, Object> {
        @Override
        public void extract(CustomerProfile target, Object argument, ValueCollector<Object> collector) {
            collector.addObject(target.getName() + "#" + target.level);
        }
    }

    public static final class CustomerProfile implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String name;
        public final int level;
        private final Address address;

        CustomerProfile(String name, int level, Address address) {
            this.name = name;
            this.level = level;
            this.address = address;
        }

        public String getName() {
            return name;
        }

        public Address getAddress() {
            return address;
        }
    }

    public static final class Address implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String city;

        Address(String city) {
            this.city = city;
        }
    }

    public record CompactEmployee(String name, int age) {
    }

    public static final class JsonProfile {
        private String name;
        private int level;

        public JsonProfile() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getLevel() {
            return level;
        }

        public void setLevel(int level) {
            this.level = level;
        }
    }

    public static final class DeployableTask {
        public static final class Marker {
        }
    }
}
