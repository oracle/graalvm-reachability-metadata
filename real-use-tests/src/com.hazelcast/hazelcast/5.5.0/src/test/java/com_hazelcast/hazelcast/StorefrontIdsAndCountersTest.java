/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_hazelcast.hazelcast;

import com.hazelcast.cardinality.CardinalityEstimator;
import com.hazelcast.config.CardinalityEstimatorConfig;
import com.hazelcast.config.Config;
import com.hazelcast.config.FlakeIdGeneratorConfig;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.PNCounterConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.crdt.pncounter.PNCounter;
import com.hazelcast.flakeidgen.FlakeIdGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises cluster-wide ID generation and counting: Flake ID generators, PN counters and
 * cardinality estimators.
 *
 * Two embedded members serve one storefront. Order numbers come from a Flake ID generator
 * with a custom epoch, so numbers drawn concurrently on both members never collide and
 * carry the time they were issued and the member that issued them. Basket items are counted
 * on a PN counter that both members increment and decrement at once and that converges to
 * the same total on every member. Distinct shoppers are estimated by a cardinality estimator
 * that ignores repeat visits and keeps its estimate when a member leaves the cluster.
 */
class StorefrontIdsAndCountersTest {

    private static final String ORDER_NUMBERS = "order-numbers";

    private static final String BASKET_ITEMS = "basket-items";

    private static final String SHOPPERS = "shoppers";

    private static final long EPOCH_START = 1_704_067_200_000L;

    private static final int BITS_SEQUENCE = 6;

    private static final int BITS_NODE_ID = 16;

    private static final long NODE_ID_OFFSET = 100;

    private HazelcastInstance first;

    private HazelcastInstance second;

    @BeforeEach
    void openStorefront() {
        String clusterName = "storefront-" + UUID.randomUUID();
        first = Hazelcast.newHazelcastInstance(memberConfig(clusterName));
        second = Hazelcast.newHazelcastInstance(memberConfig(clusterName));
        assertThat(first.getCluster().getMembers()).hasSize(2);
    }

    @AfterEach
    void closeStorefront() {
        if (second != null) {
            second.shutdown();
        }
        if (first != null) {
            first.shutdown();
        }
    }

    @Test
    void orderNumbersAreUniqueAcrossMembersAndEncodeTimeAndIssuer() throws Exception {
        FlakeIdGenerator firstNumbers = first.getFlakeIdGenerator(ORDER_NUMBERS);
        FlakeIdGenerator secondNumbers = second.getFlakeIdGenerator(ORDER_NUMBERS);
        long issuedFrom = System.currentTimeMillis();

        ExecutorService checkouts = Executors.newFixedThreadPool(4);
        try {
            List<Future<List<Long>>> batches = new ArrayList<>();
            for (int checkout = 0; checkout < 4; checkout++) {
                FlakeIdGenerator numbers = checkout % 2 == 0 ? firstNumbers : secondNumbers;
                batches.add(checkouts.submit(drawOrderNumbers(numbers, 500)));
            }
            Set<Long> allNumbers = new HashSet<>();
            List<Set<Long>> issuersPerCheckout = new ArrayList<>();
            for (Future<List<Long>> batch : batches) {
                List<Long> numbers = batch.get(60, TimeUnit.SECONDS);
                assertThat(numbers).hasSize(500).isSorted();
                allNumbers.addAll(numbers);
                Set<Long> issuers = new HashSet<>();
                for (long number : numbers) {
                    issuers.add(issuerOf(number));
                }
                issuersPerCheckout.add(issuers);
            }
            long issuedUntil = System.currentTimeMillis();

            assertThat(allNumbers).hasSize(2_000);
            for (long number : allNumbers) {
                assertThat(number).isPositive();
                assertThat(issuedAt(number)).isBetween(issuedFrom - 1_000, issuedUntil + 15_000);
            }
            Set<Long> firstIssuers = issuersPerCheckout.get(0);
            Set<Long> secondIssuers = issuersPerCheckout.get(1);
            assertThat(firstIssuers).hasSize(1).isEqualTo(issuersPerCheckout.get(2));
            assertThat(secondIssuers).hasSize(1).isEqualTo(issuersPerCheckout.get(3));
            assertThat(firstIssuers).doesNotContainAnyElementsOf(secondIssuers);
            assertThat(firstIssuers.iterator().next()).isGreaterThanOrEqualTo(NODE_ID_OFFSET);
        } finally {
            checkouts.shutdownNow();
        }
    }

    @Test
    void basketCounterConvergesOnEveryMemberAfterConcurrentUpdates() throws Exception {
        PNCounter firstBasket = first.getPNCounter(BASKET_ITEMS);
        PNCounter secondBasket = second.getPNCounter(BASKET_ITEMS);
        assertThat(firstBasket.get()).isZero();

        ExecutorService shoppers = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> updates = new ArrayList<>();
            updates.add(shoppers.submit(() -> addItems(firstBasket, 300, 2)));
            updates.add(shoppers.submit(() -> addItems(secondBasket, 200, 3)));
            updates.add(shoppers.submit(() -> removeItems(firstBasket, 150)));
            updates.add(shoppers.submit(() -> removeItems(secondBasket, 100)));
            for (Future<?> update : updates) {
                update.get(60, TimeUnit.SECONDS);
            }
        } finally {
            shoppers.shutdownNow();
        }

        long expected = 300 * 2 + 200 * 3 - 150 - 100;
        assertThat(awaitValue(firstBasket::get, expected)).isEqualTo(expected);
        assertThat(awaitValue(secondBasket::get, expected)).isEqualTo(expected);

        assertThat(secondBasket.getAndAdd(5)).isEqualTo(expected);
        assertThat(secondBasket.subtractAndGet(2)).isEqualTo(expected + 3);
        assertThat(awaitValue(firstBasket::get, expected + 3)).isEqualTo(expected + 3);
        assertThat(first.getPNCounter(BASKET_ITEMS).decrementAndGet()).isEqualTo(expected + 2);
    }

    @Test
    void shopperEstimateIgnoresRepeatVisitsAndSurvivesAMemberLeaving() throws Exception {
        CardinalityEstimator firstShoppers = first.getCardinalityEstimator(SHOPPERS);
        CardinalityEstimator secondShoppers = second.getCardinalityEstimator(SHOPPERS);
        assertThat(firstShoppers.estimate()).isZero();

        firstShoppers.add("shopper-alice");
        secondShoppers.add("shopper-bob");
        firstShoppers.add("shopper-alice");
        secondShoppers.addAsync("shopper-carol").toCompletableFuture().get(30, TimeUnit.SECONDS);
        assertThat(secondShoppers.estimate()).isEqualTo(3);

        for (int visit = 0; visit < 3; visit++) {
            for (int shopper = 0; shopper < 5_000; shopper++) {
                CardinalityEstimator entrance = shopper % 2 == 0 ? firstShoppers : secondShoppers;
                entrance.add("shopper-" + shopper);
            }
        }
        long estimate = firstShoppers.estimateAsync().toCompletableFuture().get(30, TimeUnit.SECONDS);
        assertThat(estimate).isBetween(4_850L, 5_150L);
        assertThat(secondShoppers.estimate()).isEqualTo(estimate);

        first.shutdown();
        first = null;
        assertThat(awaitValue(() -> second.getCluster().getMembers().size(), 1)).isEqualTo(1);
        assertThat(second.getCardinalityEstimator(SHOPPERS).estimate()).isEqualTo(estimate);
    }

    private static Callable<List<Long>> drawOrderNumbers(FlakeIdGenerator numbers, int count) {
        return () -> {
            List<Long> drawn = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                drawn.add(numbers.newId());
            }
            return drawn;
        };
    }

    private static void addItems(PNCounter basket, int times, int quantity) {
        for (int index = 0; index < times; index++) {
            basket.addAndGet(quantity);
        }
    }

    private static void removeItems(PNCounter basket, int times) {
        for (int index = 0; index < times; index++) {
            basket.decrementAndGet();
        }
    }

    /// Polls a source until it reports the expected value or a minute has passed.
    private static long awaitValue(LongSupplier source, long expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        long value = source.getAsLong();
        while (value != expected && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(50);
            value = source.getAsLong();
        }
        return value;
    }

    private static long issuedAt(long orderNumber) {
        return (orderNumber >>> (BITS_SEQUENCE + BITS_NODE_ID)) + EPOCH_START;
    }

    private static long issuerOf(long orderNumber) {
        return orderNumber & ((1L << BITS_NODE_ID) - 1);
    }

    private static Config memberConfig(String clusterName) {
        Config config = new Config();
        config.setClusterName(clusterName);
        config.setProperty("hazelcast.phone.home.enabled", "false");
        config.addFlakeIdGeneratorConfig(new FlakeIdGeneratorConfig(ORDER_NUMBERS)
                .setEpochStart(EPOCH_START)
                .setBitsSequence(BITS_SEQUENCE)
                .setBitsNodeId(BITS_NODE_ID)
                .setNodeIdOffset(NODE_ID_OFFSET)
                .setPrefetchCount(50));
        config.addPNCounterConfig(new PNCounterConfig(BASKET_ITEMS).setReplicaCount(2));
        config.addCardinalityEstimatorConfig(new CardinalityEstimatorConfig(SHOPPERS).setBackupCount(1));
        config.getNetworkConfig().setPort(5701).setPortAutoIncrement(true);
        JoinConfig join = config.getNetworkConfig().getJoin();
        join.getMulticastConfig().setEnabled(false);
        join.getAutoDetectionConfig().setEnabled(false);
        join.getTcpIpConfig().setEnabled(true).addMember("127.0.0.1");
        config.getNetworkConfig().getInterfaces().setEnabled(true).addInterface("127.0.0.1");
        return config;
    }
}
