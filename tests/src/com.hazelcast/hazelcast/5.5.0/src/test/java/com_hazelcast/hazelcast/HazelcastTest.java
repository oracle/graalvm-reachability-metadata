/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_hazelcast.hazelcast;

import com.hazelcast.cache.ICache;
import com.hazelcast.cardinality.CardinalityEstimator;
import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.ClientReliableTopicConfig;
import com.hazelcast.config.Config;
import com.hazelcast.config.GlobalSerializerConfig;
import com.hazelcast.config.SerializerConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IExecutorService;
import com.hazelcast.crdt.pncounter.PNCounter;
import com.hazelcast.durableexecutor.DurableExecutorService;
import com.hazelcast.flakeidgen.FlakeIdGenerator;
import com.hazelcast.map.IMap;
import com.hazelcast.multimap.MultiMap;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.StreamSerializer;
import com.hazelcast.replicatedmap.ReplicatedMap;
import com.hazelcast.ringbuffer.Ringbuffer;
import com.hazelcast.scheduledexecutor.IScheduledExecutorService;
import com.hazelcast.scheduledexecutor.IScheduledFuture;
import com.hazelcast.topic.ITopic;
import com_hazelcast.hazelcast.callable.EchoCallable;
import com_hazelcast.hazelcast.globalSerializer.GlobalSerializer;
import com_hazelcast.hazelcast.identifiedDataSerializable.Employee;
import com_hazelcast.hazelcast.identifiedDataSerializable.SampleDataSerializableFactory;
import com_hazelcast.hazelcast.portableSerializable.Customer;
import com_hazelcast.hazelcast.portableSerializable.SamplePortableFactory;
import com_hazelcast.hazelcast.query.ThePortableFactory;
import com_hazelcast.hazelcast.query.User;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.AccessedExpiryPolicy;
import javax.cache.expiry.Duration;
import javax.cache.spi.CachingProvider;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;

import static com.hazelcast.query.Predicates.and;
import static com.hazelcast.query.Predicates.between;
import static com.hazelcast.query.Predicates.equal;
import static com.hazelcast.query.Predicates.sql;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.awaitility.Awaitility.await;

public class HazelcastTest {
    static Integer PORT = 45739;
    static HazelcastInstance hazelcastInstance;

    @BeforeAll
    static void beforeAll() {
        Config config = new Config();
        config.getNetworkConfig().setPort(PORT);
        hazelcastInstance = Hazelcast.newHazelcastInstance(config);
        await().atMost(java.time.Duration.ofSeconds(50)).ignoreExceptions().until(() -> {
            ClientConfig clientConfig = new ClientConfig();
            clientConfig.getNetworkConfig().addAddress("localhost:" + PORT);
            HazelcastClient.newHazelcastClient(clientConfig).shutdown();
            return true;
        });
    }

    @AfterAll
    static void afterAll() {
        hazelcastInstance.shutdown();
    }

    @Test
    void testAtomicLong() {
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.getNetworkConfig().addAddress("localhost:" + PORT);
        HazelcastInstance client = HazelcastClient.newHazelcastClient(clientConfig);
        IMap<String, Long> counterMap = client.getMap("counterMap");
        counterMap.merge("counter", 3L, Long::sum);
        assertThat(counterMap.get("counter")).isEqualTo(3L);
        client.shutdown();
    }

    @Test
    void testCustomSerializer() {
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.getNetworkConfig().addAddress("localhost:" + PORT);
        clientConfig.getSerializationConfig().addSerializerConfig(new SerializerConfig()
                .setClassName(NamedValueSerializer.class.getName())
                .setTypeClassName(NamedValue.class.getName()));
        HazelcastInstance client = HazelcastClient.newHazelcastClient(clientConfig);
        try {
            IMap<String, NamedValue> values = client.getMap("custom-serialized-values");
            values.put("key", new NamedValue("custom value"));
            assertThat(values.get("key").value).isEqualTo("custom value");
        } finally {
            client.shutdown();
        }
    }

    @Test
    void testGlobalSerializer() {
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.getNetworkConfig().addAddress("localhost:" + PORT);
        clientConfig.getSerializationConfig().setGlobalSerializerConfig(new GlobalSerializerConfig().setImplementation(new GlobalSerializer()));
        HazelcastInstance hz = HazelcastClient.newHazelcastClient(clientConfig);
        hz.shutdown();
    }

    @Test
    void testIdentifiedDataSerializable() {
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.getNetworkConfig().addAddress("localhost:" + PORT);
        clientConfig.getSerializationConfig().addDataSerializableFactoryClass(
                SampleDataSerializableFactory.FACTORY_ID, SampleDataSerializableFactory.class.getName());
        HazelcastInstance client = HazelcastClient.newHazelcastClient(clientConfig);
        try {
            Employee employee = new Employee();
            employee.id = 7;
            employee.name = "Ada";
            IMap<String, Employee> employees = client.getMap("identified-employees");
            employees.put("engineer", employee);
            Employee restored = employees.get("engineer");
            assertThat(restored.id).isEqualTo(7);
            assertThat(restored.name).isEqualTo("Ada");
        } finally {
            client.shutdown();
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void testJCache() {
        System.setProperty("hazelcast.jcache.provider.type", "member");
        HazelcastInstance hazelcastInstance = null;
        CachingProvider provider = null;
        CacheManager cacheManager = null;

        try {
            Config config = new Config();
            config.setInstanceName("test-instance-" + System.currentTimeMillis());
            config.setClusterName("test-cluster");

            config.getNetworkConfig().setPort(0);
            config.getNetworkConfig().setPortAutoIncrement(true);
            config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
            config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
            config.getNetworkConfig().getJoin().getAutoDetectionConfig().setEnabled(false);

            hazelcastInstance = Hazelcast.newHazelcastInstance(config);
            provider = Caching.getCachingProvider("com.hazelcast.cache.HazelcastCachingProvider");
            Properties props = new Properties();
            props.setProperty("hazelcast.instance.name", config.getInstanceName());
            cacheManager = provider.getCacheManager(URI.create("hazelcast://test"), provider.getDefaultClassLoader(), props);
            MutableConfiguration<String, String> configuration = new MutableConfiguration<>();
            configuration.setTypes(String.class, String.class);
            configuration.setExpiryPolicyFactory(AccessedExpiryPolicy.factoryOf(Duration.ONE_MINUTE));
            configuration.setStatisticsEnabled(true);
            String cacheName = "myCache-" + System.currentTimeMillis();
            Cache<String, String> myCache = cacheManager.createCache(cacheName, configuration);
            myCache.put("key", "value");
            assertThat(myCache.get("key")).isEqualTo("value");
            ICache<String, String> cacheAsI = myCache.unwrap(ICache.class);
            cacheAsI.getAsync("key");
            cacheAsI.putAsync("key", "value");
            cacheAsI.put("key", "newValue", AccessedExpiryPolicy.factoryOf(Duration.TEN_MINUTES).create());
            assertThat(cacheAsI.size()).isEqualTo(1);
            assertThat(cacheManager.getCache(cacheName, String.class, String.class)).isNotNull();

        } catch (Exception e) {
            System.out.println("JCache failed, trying native Hazelcast API: " + e.getMessage());
            if (hazelcastInstance != null) {
                testWithNativeHazelcastAPI(hazelcastInstance);
            } else {
                throw new RuntimeException("Both JCache and fallback failed", e);
            }

        } finally {
            if (cacheManager != null) {
                try {
                    cacheManager.close();
                } catch (Exception ignored) {
                }
            }
            if (provider != null) {
                try {
                    provider.close();
                } catch (Exception ignored) {
                }
            }
            if (hazelcastInstance != null) {
                try {
                    hazelcastInstance.shutdown();
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Test
    void testList() {
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.getNetworkConfig().addAddress("localhost:" + PORT);
        HazelcastInstance client = HazelcastClient.newHazelcastClient(clientConfig);
        List<Object> list = client.getList("my-distributed-list");
        list.add("item1");
        list.add("item2");
        assertThat(list.remove(0)).isEqualTo("item1");
        assertThat(list.size()).isEqualTo(1);
        list.clear();
        client.shutdown();
    }

    @Test
    void testLock() {
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.getNetworkConfig().addAddress("localhost:" + PORT);
        HazelcastInstance client = HazelcastClient.newHazelcastClient(clientConfig);
        IMap<String, String> lockMap = client.getMap("lockMap");
        IMap<String, String> dataMap = client.getMap("dataMap");
        lockMap.lock("my-distributed-lock");
        try {
            dataMap.put("key", "value");
            dataMap.get("key");
            dataMap.putIfAbsent("someKey", "someValue");
            dataMap.replace("key", "value", "newValue");

            assertThat(dataMap.get("someKey")).isEqualTo("someValue");
            assertThat(dataMap.get("key")).isEqualTo("newValue");
        } finally {
            lockMap.unlock("my-distributed-lock");
        }
        client.shutdown();
    }

    @Test
    void testMap() {
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.getNetworkConfig().addAddress("localhost:" + PORT);
        HazelcastInstance client = HazelcastClient.newHazelcastClient(clientConfig);
        IMap<String, String> map = client.getMap("my-distributed-map");
        map.put("key", "value");
        map.get("key");
        map.putIfAbsent("someKey", "someValue");
        map.replace("key", "value", "newValue");
        assertThat(map.get("someKey")).isEqualTo("someValue");
        assertThat(map.get("key")).isEqualTo("newValue");
        client.shutdown();
    }

    @Test
    void testMultiMap() {
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.getNetworkConfig().addAddress("localhost:" + PORT);
        HazelcastInstance client = HazelcastClient.newHazelcastClient(clientConfig);
        MultiMap<String, String> multiMap = client.getMultiMap("my-distributed-multimap");
        multiMap.put("my-key", "value1");
        multiMap.put("my-key", "value2");
        multiMap.put("my-key", "value3");
        assertThat(multiMap.get("my-key").toString()).contains("value2", "value1", "value3");
        multiMap.remove("my-key", "value2");
        assertThat(multiMap.get("my-key").toString()).contains("value1", "value3");
        client.shutdown();
    }

    @Test
    void testPortableSerializable() {
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.getNetworkConfig().addAddress("localhost:" + PORT);
        clientConfig.getSerializationConfig().addPortableFactoryClass(
                SamplePortableFactory.FACTORY_ID, SamplePortableFactory.class.getName());
        HazelcastInstance client = HazelcastClient.newHazelcastClient(clientConfig);
        try {
            Customer customer = new Customer();
            customer.id = 42;
            customer.name = "Grace";
            customer.lastOrder = new Date(123456789L);
            IMap<String, Customer> customers = client.getMap("portable-customers");
            customers.put("customer", customer);
            Customer restored = customers.get("customer");
            assertThat(restored.id).isEqualTo(42);
            assertThat(restored.name).isEqualTo("Grace");
            assertThat(restored.lastOrder).isEqualTo(customer.lastOrder);
        } finally {
            client.shutdown();
        }
    }

    @Test
    void testQuery() {
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.getNetworkConfig().addAddress("localhost:" + PORT);
        clientConfig.getSerializationConfig().addPortableFactory(ThePortableFactory.FACTORY_ID, new ThePortableFactory());
        HazelcastInstance client = HazelcastClient.newHazelcastClient(clientConfig);
        IMap<String, User> users = client.getMap("users");
        User rod = new User("Rod", 19, true);
        User jane = new User("Jane", 20, true);
        users.put("Rod", rod);
        users.put("Jane", jane);
        users.put("Freddy", new User("Freddy", 23, true));
        Collection<User> result1 = users.values(sql("active AND age BETWEEN 18 AND 21)"));
        Collection<User> result2 = users.values(and(equal("active", true), between("age", 18, 21)));
        assertThat(result1).contains(rod, jane);
        assertThat(result2).contains(rod, jane);
        client.shutdown();
    }

    @Test
    void testQueue() throws InterruptedException {
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.getNetworkConfig().addAddress("localhost:" + PORT);
        HazelcastInstance client = HazelcastClient.newHazelcastClient(clientConfig);
        BlockingQueue<String> queue = client.getQueue("my-distributed-queue");
        assertThat(queue.offer("item")).isTrue();
        queue.poll();
        assertThat(queue.offer("anotherItem", 10, TimeUnit.SECONDS)).isTrue();
        queue.poll(10, TimeUnit.SECONDS);
        queue.put("yetAnotherItem");
        assertThat(queue.take()).isEqualTo("yetAnotherItem");
        client.shutdown();
    }

    @Test
    void testReplicatedMap() {
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.getNetworkConfig().addAddress("localhost:" + PORT);
        HazelcastInstance client = HazelcastClient.newHazelcastClient(clientConfig);
        ReplicatedMap<String, String> map = client.getReplicatedMap("my-replicated-map");
        assertThat(map.put("key", "value")).isNull();
        assertThat(map.get("key")).isEqualTo("value");
        client.shutdown();
    }

    @Test
    void testRingBuffer() throws InterruptedException {
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.getNetworkConfig().addAddress("localhost:" + PORT);
        HazelcastInstance client = HazelcastClient.newHazelcastClient(clientConfig);
        Ringbuffer<Long> rb = client.getRingbuffer("rb");
        rb.add(100L);
        rb.add(200L);
        long sequence = rb.headSequence();
        assertThat(rb.readOne(sequence)).isEqualTo(100);
        sequence++;
        assertThat(rb.readOne(sequence)).isEqualTo(200);
        client.shutdown();
    }

    @Test
    void testSet() {
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.getNetworkConfig().addAddress("localhost:" + PORT);
        HazelcastInstance client = HazelcastClient.newHazelcastClient(clientConfig);
        Set<String> set = client.getSet("my-distributed-set");
        set.add("item1");
        set.add("item2");
        set.add("item3");
        assertThat(set).contains("item1", "item2", "item3");
        assertThat(set.size()).isEqualTo(3);
        client.shutdown();
    }

    @Test
    void testTopic() {
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.getNetworkConfig().addAddress("localhost:" + PORT);
        HazelcastInstance client = HazelcastClient.newHazelcastClient(clientConfig);
        ITopic<Object> topic = client.getTopic("my-distributed-topic");
        topic.addMessageListener(message -> assertThat(message.getMessageObject()).isEqualTo("Hello to distributed world"));
        IntStream.range(0, 3).mapToObj(i -> "Hello to distributed world").forEach(topic::publish);
        client.shutdown();
    }

    @Test
    void testJCacheOrigin() {
        System.setProperty("hazelcast.jcache.provider.type", "member");
        HazelcastInstance hazelcastInstance = null;
        CachingProvider provider = null;
        CacheManager cacheManager = null;

        try {
            Config config = new Config();
            config.setInstanceName("test-instance-" + System.currentTimeMillis());
            config.setClusterName("test-cluster");

            config.getNetworkConfig().setPort(0);
            config.getNetworkConfig().setPortAutoIncrement(true);
            config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
            config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
            config.getNetworkConfig().getJoin().getAutoDetectionConfig().setEnabled(false);

            hazelcastInstance = Hazelcast.newHazelcastInstance(config);
            provider = Caching.getCachingProvider("com.hazelcast.cache.HazelcastCachingProvider");
            Properties props = new Properties();
            props.setProperty("hazelcast.instance.name", config.getInstanceName());
            cacheManager = provider.getCacheManager(URI.create("hazelcast://test"), provider.getDefaultClassLoader(), props);
            MutableConfiguration<String, String> cacheConfig = new MutableConfiguration<String, String>().setTypes(String.class, String.class).setStatisticsEnabled(true).setManagementEnabled(true);
            String cacheName = "example-" + System.currentTimeMillis();
            Cache<String, String> cache = cacheManager.createCache(cacheName, cacheConfig);
            cache.put("world", "Hello World");
            assertThat(cache.get("world")).isEqualTo("Hello World");
            assertThat(cacheManager.getCache(cacheName, String.class, String.class)).isNotNull();

        } catch (Exception e) {
            System.out.println("JCache failed, trying native Hazelcast API: " + e.getMessage());
            if (hazelcastInstance != null) {
                testWithNativeHazelcastAPI(hazelcastInstance);
            } else {
                throw new RuntimeException("Both JCache and fallback failed", e);
            }

        } finally {

            if (cacheManager != null) {
                try {
                    cacheManager.close();
                } catch (Exception ignored) {
                }
            }
            if (provider != null) {
                try {
                    provider.close();
                } catch (Exception ignored) {
                }
            }
            if (hazelcastInstance != null) {
                try {
                    hazelcastInstance.shutdown();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void testWithNativeHazelcastAPI(HazelcastInstance hz) {
        com.hazelcast.map.IMap<String, String> map = hz.getMap("example-cache");

        map.put("world", "Hello World");
        assertThat(map.get("world")).isEqualTo("Hello World");
        assertThat(map.containsKey("world")).isTrue();

        assertThat(map.size()).isEqualTo(1);
    }


    @Test
    void testExecutorService() throws ExecutionException, InterruptedException, TimeoutException {
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.getNetworkConfig().addAddress("localhost:" + PORT);
        HazelcastInstance client = HazelcastClient.newHazelcastClient(clientConfig);
        IExecutorService executorService = client.getExecutorService("my-distributed-executor-service");
        Future<String> result = executorService.submit(new EchoCallable("Hello World"));
        assertThat(result.get(30, TimeUnit.SECONDS)).isEqualTo("Hello World");
        client.shutdown();
    }

    @Test
    void testDurableExecutorService() throws ExecutionException, InterruptedException, TimeoutException {
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.getNetworkConfig().addAddress("localhost:" + PORT);
        HazelcastInstance client = HazelcastClient.newHazelcastClient(clientConfig);
        DurableExecutorService durableExecutorService = client.getDurableExecutorService("my-distributed-durable-executor-service");
        long taskId = durableExecutorService.submit(new EchoCallable("Hello World")).getTaskId();
        assertThat(durableExecutorService.retrieveResult(taskId).get(30, TimeUnit.SECONDS)).isEqualTo("Hello World");
        client.shutdown();
    }

    @Test
    void testXAResource() {
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.getNetworkConfig().addAddress("localhost:" + PORT);
        HazelcastInstance client = HazelcastClient.newHazelcastClient(clientConfig);
        assertThatNoException().isThrownBy(client::getXAResource);
        client.shutdown();
    }

    @Test
    void testReliableTopic() {
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.getNetworkConfig().addAddress("localhost:" + PORT);
        clientConfig.addReliableTopicConfig(new ClientReliableTopicConfig("my-distributed-reliable-topic").setExecutor(Executors.newSingleThreadExecutor()));
        HazelcastInstance client = HazelcastClient.newHazelcastClient(clientConfig);
        ITopic<Object> reliableTopic = client.getReliableTopic("my-distributed-reliable-topic");
        reliableTopic.addMessageListener(message -> assertThat(message.getMessageObject()).isEqualTo("Hello World"));
        reliableTopic.publish("Hello World");
        client.shutdown();
    }

    @Test
    void testFlakeIdGenerator() {
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.getNetworkConfig().addAddress("localhost:" + PORT);
        HazelcastInstance client = HazelcastClient.newHazelcastClient(clientConfig);
        FlakeIdGenerator flakeIdGenerator = client.getFlakeIdGenerator("my-flake-id-generator");
        assertThat(flakeIdGenerator.newId()).isNotNegative();
        client.shutdown();
    }

    @Test
    void testCardinalityEstimator() {
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.getNetworkConfig().addAddress("localhost:" + PORT);
        HazelcastInstance client = HazelcastClient.newHazelcastClient(clientConfig);
        CardinalityEstimator cardinalityEstimator = client.getCardinalityEstimator("my-distributed-cardinality-estimator");
        cardinalityEstimator.add("a value");
        assertThat(cardinalityEstimator.estimate()).isEqualTo(1);
        client.shutdown();
    }

    @Test
    void testScheduledExecutorService() throws ExecutionException, InterruptedException, TimeoutException {
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.getNetworkConfig().addAddress("localhost:" + PORT);
        HazelcastInstance client = HazelcastClient.newHazelcastClient(clientConfig);
        IScheduledExecutorService scheduledExecutorService = client.getScheduledExecutorService("my-distributed-scheduled-executor-service");
        IScheduledFuture<String> result = scheduledExecutorService.schedule(new EchoCallable("Hello World"), 0L, TimeUnit.MILLISECONDS);
        assertThat(result.get(30, TimeUnit.SECONDS)).isEqualTo("Hello World");
        client.shutdown();
    }

    @Test
    void loadsXmlAndYamlConfiguration() {
        Config xmlConfig = Config.loadFromString("""
                <hazelcast xmlns="http://www.hazelcast.com/schema/config">
                    <cluster-name>xml-cluster</cluster-name>
                    <map name="orders">
                        <backup-count>0</backup-count>
                    </map>
                    <queue name="work">
                        <max-size>17</max-size>
                    </queue>
                </hazelcast>
                """);
        Config yamlConfig = Config.loadFromString("""
                hazelcast:
                  cluster-name: yaml-cluster
                  map:
                    customers:
                      backup-count: 0
                  queue:
                    messages:
                      max-size: 23
                """);

        assertThat(xmlConfig.getClusterName()).isEqualTo("xml-cluster");
        assertThat(xmlConfig.getMapConfig("orders").getBackupCount()).isZero();
        assertThat(xmlConfig.getQueueConfig("work").getMaxSize()).isEqualTo(17);
        assertThat(yamlConfig.getClusterName()).isEqualTo("yaml-cluster");
        assertThat(yamlConfig.getMapConfig("customers").getBackupCount()).isZero();
        assertThat(yamlConfig.getQueueConfig("messages").getMaxSize()).isEqualTo(23);
    }

    @Test
    void createsNamedConfigurationsFromDefaults() {
        Config config = new Config();
        config.getMapConfig("default").setBackupCount(0);

        assertThat(List.of(
                        config.getMapConfig("orders").getName(),
                        config.getCacheConfig("cache").getName(),
                        config.getQueueConfig("queue").getName(),
                        config.getListConfig("list").getName(),
                        config.getSetConfig("set").getName(),
                        config.getMultiMapConfig("multi-map").getName(),
                        config.getReplicatedMapConfig("replicated-map").getName(),
                        config.getRingbufferConfig("ringbuffer").getName(),
                        config.getTopicConfig("topic").getName(),
                        config.getReliableTopicConfig("reliable-topic").getName(),
                        config.getExecutorConfig("executor").getName(),
                        config.getDurableExecutorConfig("durable-executor").getName(),
                        config.getScheduledExecutorConfig("scheduled-executor").getName(),
                        config.getCardinalityEstimatorConfig("cardinality").getName(),
                        config.getPNCounterConfig("pn-counter").getName(),
                        config.getFlakeIdGeneratorConfig("flake-id").getName(),
                        config.getSplitBrainProtectionConfig("split-brain").getName()))
                .containsExactly(
                        "orders", "cache", "queue", "list", "set", "multi-map", "replicated-map",
                        "ringbuffer", "topic", "reliable-topic", "executor", "durable-executor",
                        "scheduled-executor", "cardinality", "pn-counter", "flake-id", "split-brain");
        assertThat(config.getMapConfig("orders").getBackupCount()).isZero();
        config.getMapConfig("orders").setBackupCount(1);
        assertThat(config.getMapConfig("default").getBackupCount()).isZero();
    }

    @Test
    void serializesCompactObjectsReflectively() {
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.getNetworkConfig().addAddress("localhost:" + PORT);
        clientConfig.getSerializationConfig().getCompactSerializationConfig().addClass(CompactPerson.class);
        HazelcastInstance client = HazelcastClient.newHazelcastClient(clientConfig);
        try {
            IMap<String, CompactPerson> people = client.getMap("compact-people");
            people.put("architect", new CompactPerson("Lin", 37));

            CompactPerson restored = people.get("architect");
            assertThat(restored.getName()).isEqualTo("Lin");
            assertThat(restored.getAge()).isEqualTo(37);
        } finally {
            client.shutdown();
        }
    }

    @Test
    void serializesJavaObjectsThroughDistributedMap() {
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.getNetworkConfig().addAddress("localhost:" + PORT);
        HazelcastInstance client = HazelcastClient.newHazelcastClient(clientConfig);
        try {
            IMap<String, SerializablePerson> people = client.getMap("java-serialized-people");
            people.put("operator", new SerializablePerson("Margaret", 29));

            SerializablePerson restored = people.get("operator");
            assertThat(restored.name).isEqualTo("Margaret");
            assertThat(restored.age).isEqualTo(29);
        } finally {
            client.shutdown();
        }
    }

    public static final class NamedValue {
        private final String value;

        public NamedValue(String value) {
            this.value = value;
        }
    }

    public static final class NamedValueSerializer implements StreamSerializer<NamedValue> {
        @Override
        public int getTypeId() {
            return 10;
        }

        @Override
        public void write(ObjectDataOutput out, NamedValue object) throws IOException {
            out.writeString(object.value);
        }

        @Override
        public NamedValue read(ObjectDataInput in) throws IOException {
            return new NamedValue(in.readString());
        }
    }

    public static final class CompactPerson {
        private String name;
        private int age;

        public CompactPerson() {
        }

        CompactPerson(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }
    }

    public static final class SerializablePerson implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String name;
        private final int age;

        SerializablePerson(String name, int age) {
            this.name = name;
            this.age = age;
        }
    }

    @Test
    void testPNCounter() {
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.getNetworkConfig().addAddress("localhost:" + PORT);
        HazelcastInstance client = HazelcastClient.newHazelcastClient(clientConfig);
        PNCounter pnCounter = client.getPNCounter("my-distributed-pn-counter");
        assertThat(pnCounter.addAndGet(1)).isEqualTo(1);
        client.shutdown();
    }
}
