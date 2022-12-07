/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_hazelcast.hazelcast;

import com.hazelcast.cache.HazelcastCachingProvider;
import com.hazelcast.cache.ICache;
import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.multimap.MultiMap;
import com.hazelcast.replicatedmap.ReplicatedMap;
import com.hazelcast.topic.ITopic;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.AccessedExpiryPolicy;
import javax.cache.expiry.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.stream.IntStream;

import static com.hazelcast.query.Predicates.*;
import static org.assertj.core.api.Assertions.assertThat;

class HazelcastTest {
    @BeforeAll
    static void before() {
        Hazelcast.shutdownAll();
        Hazelcast.newHazelcastInstance(new Config());
    }

    @AfterAll
    static void after() {
        Hazelcast.shutdownAll();
    }

    @Test
    void testMap() {
        HazelcastInstance client = HazelcastClient.newHazelcastClient();
        IMap<String, String> map = client.getMap("my-distributed-map");
        map.put("key", "value");
        map.get("key");
        map.putIfAbsent("someKey", "someValue");
        map.replace("key", "value", "newValue");
        assertThat(map.get("someKey")).isEqualTo("someValue");
        assertThat(map.get("key")).isEqualTo("newValue");
        client.shutdown();
    }

    @SuppressWarnings("unchecked")
    @Test
    void testJCache() {
        System.setProperty("hazelcast.jcache.provider.type", "client");
        CacheManager manager = Caching.getCachingProvider(HazelcastCachingProvider.class.getName()).getCacheManager();
        MutableConfiguration<String, String> configuration = new MutableConfiguration<>();
        configuration.setExpiryPolicyFactory(AccessedExpiryPolicy.factoryOf(Duration.ONE_MINUTE));
        Cache<String, String> myCache = manager.createCache("myCache", configuration);
        myCache.put("key", "value");
        assertThat(myCache.get("key")).isEqualTo("value");
        ICache<String, String> cacheAsI = myCache.unwrap(ICache.class);
        cacheAsI.getAsync("key");
        cacheAsI.putAsync("key", "value");
        cacheAsI.put("key", "newValue", AccessedExpiryPolicy.factoryOf(Duration.TEN_MINUTES).create());
        assertThat(cacheAsI.size()).isEqualTo(1);
        manager.getCachingProvider().close();
    }

    @Test
    void testReplicatedMap() {
        HazelcastInstance client = HazelcastClient.newHazelcastClient();
        ReplicatedMap<String, String> map = client.getReplicatedMap("my-replicated-map");
        assertThat(map.put("key", "value")).isNull();
        assertThat(map.get("key")).isEqualTo("value");
        client.shutdown();
    }

    @Test
    void testMultiMap() {
        HazelcastInstance client = HazelcastClient.newHazelcastClient();
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
    void testSet() {
        HazelcastInstance client = HazelcastClient.newHazelcastClient();
        Set<String> set = client.getSet("my-distributed-set");
        set.add("item1");
        set.add("item2");
        set.add("item3");
        assertThat(set).contains("item1", "item2", "item3");
        assertThat(set.size()).isEqualTo(3);
        client.shutdown();
    }

    @Test
    void testList() {
        HazelcastInstance client = HazelcastClient.newHazelcastClient();
        List<Object> list = client.getList("my-distributed-list");
        list.add("item1");
        list.add("item2");
        assertThat(list.remove(0)).isEqualTo("item1");
        assertThat(list.size()).isEqualTo(1);
        list.clear();
        client.shutdown();
    }

    @Test
    void testQueue() throws InterruptedException {
        HazelcastInstance client = HazelcastClient.newHazelcastClient();
        BlockingQueue<String> queue = client.getQueue("my-distributed-queue");
        assertThat(queue.offer("item")).isTrue();
        queue.poll();
        assertThat(queue.offer("anotherItem", 500, TimeUnit.MILLISECONDS)).isTrue();
        queue.poll(5, TimeUnit.SECONDS);
        queue.put("yetAnotherItem");
        assertThat(queue.take()).isEqualTo("yetAnotherItem");
        client.shutdown();
    }

    @Test
    void testTopic() {
        HazelcastInstance client = HazelcastClient.newHazelcastClient();
        ITopic<Object> topic = client.getTopic("my-distributed-topic");
        topic.addMessageListener(message -> assertThat(message.getMessageObject()).isEqualTo("Hello to distributed world"));
        IntStream.range(0, 3).mapToObj(i -> "Hello to distributed world").forEach(topic::publish);
        client.shutdown();
    }

    @Test
    void testQuery() {
        ClientConfig clientConfig = new ClientConfig();
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
    void testLock() {
        HazelcastInstance client = HazelcastClient.newHazelcastClient();
        Lock lock = client.getCPSubsystem().getLock("my-distributed-lock");
        lock.lock();
        try {
            IMap<String, String> map = client.getMap("lock-distributed-map");
            map.put("key", "value");
            map.get("key");
            map.putIfAbsent("someKey", "someValue");
            map.replace("key", "value", "newValue");
            assertThat(map.get("someKey")).isEqualTo("someValue");
            assertThat(map.get("key")).isEqualTo("newValue");
        } finally {
            lock.unlock();
        }
        client.shutdown();
    }
}
