/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jersey_bundles_repackaged.jersey_guava;

import java.io.ByteArrayInputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jersey.repackaged.com.google.common.base.CharMatcher;
import jersey.repackaged.com.google.common.base.Joiner;
import jersey.repackaged.com.google.common.base.MoreObjects;
import jersey.repackaged.com.google.common.base.Optional;
import jersey.repackaged.com.google.common.base.Preconditions;
import jersey.repackaged.com.google.common.base.Predicates;
import jersey.repackaged.com.google.common.base.Splitter;
import jersey.repackaged.com.google.common.base.Ticker;
import jersey.repackaged.com.google.common.cache.Cache;
import jersey.repackaged.com.google.common.cache.CacheBuilder;
import jersey.repackaged.com.google.common.cache.CacheLoader;
import jersey.repackaged.com.google.common.cache.LoadingCache;
import jersey.repackaged.com.google.common.cache.RemovalNotification;
import jersey.repackaged.com.google.common.collect.Collections2;
import jersey.repackaged.com.google.common.collect.HashBasedTable;
import jersey.repackaged.com.google.common.collect.HashMultimap;
import jersey.repackaged.com.google.common.collect.HashMultiset;
import jersey.repackaged.com.google.common.collect.ImmutableBiMap;
import jersey.repackaged.com.google.common.collect.ImmutableList;
import jersey.repackaged.com.google.common.collect.ImmutableListMultimap;
import jersey.repackaged.com.google.common.collect.ImmutableMap;
import jersey.repackaged.com.google.common.collect.ImmutableSet;
import jersey.repackaged.com.google.common.collect.Iterables;
import jersey.repackaged.com.google.common.collect.Lists;
import jersey.repackaged.com.google.common.collect.MapDifference;
import jersey.repackaged.com.google.common.collect.Maps;
import jersey.repackaged.com.google.common.collect.Multiset;
import jersey.repackaged.com.google.common.collect.Ordering;
import jersey.repackaged.com.google.common.collect.Sets;
import jersey.repackaged.com.google.common.collect.Table;
import jersey.repackaged.com.google.common.io.ByteArrayDataInput;
import jersey.repackaged.com.google.common.io.ByteStreams;
import jersey.repackaged.com.google.common.net.InetAddresses;
import jersey.repackaged.com.google.common.primitives.Ints;
import jersey.repackaged.com.google.common.primitives.UnsignedBytes;
import jersey.repackaged.com.google.common.util.concurrent.FutureCallback;
import jersey.repackaged.com.google.common.util.concurrent.Futures;
import jersey.repackaged.com.google.common.util.concurrent.ListenableFuture;
import jersey.repackaged.com.google.common.util.concurrent.MoreExecutors;
import jersey.repackaged.com.google.common.util.concurrent.SettableFuture;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class Jersey_guavaTest {
    @Test
    void baseUtilitiesHandleJoiningSplittingMatchingAndValidation() {
        Map<String, Integer> versions = new LinkedHashMap<>();
        versions.put("jersey", 2);
        versions.put("guava", 18);

        String joinedValues = Joiner.on(';').useForNull("<missing>")
                .join(Arrays.asList("alpha", null, "gamma"));
        String joinedMap = Joiner.on(',').withKeyValueSeparator("=")
                .appendTo(new StringBuilder(), versions)
                .toString();

        Iterable<String> parts = Splitter.on(',').trimResults().split(" alpha, beta" + ",,gamma ");
        CharMatcher identifierMatcher = CharMatcher.inRange('a', 'z')
                .or(CharMatcher.inRange('0', '9'))
                .or(CharMatcher.is('-'));
        String description = MoreObjects.toStringHelper("Record")
                .add("id", 7)
                .addValue("ready")
                .toString();

        assertThat(joinedValues).isEqualTo("alpha;<missing>;gamma");
        assertThat(joinedMap).isEqualTo("jersey=2,guava=18");
        assertThat(parts).containsExactly("alpha", "beta", "", "gamma");
        assertThat(identifierMatcher.matches('j')).isTrue();
        assertThat(identifierMatcher.matches('-')).isTrue();
        assertThat(identifierMatcher.matches('J')).isFalse();
        assertThat(identifierMatcher.indexIn("Jersey-2", 0)).isEqualTo(1);
        assertThat(description).isEqualTo("Record{id=7, ready}");
        assertThat(Optional.fromNullable("value").orNull()).isEqualTo("value");
        assertThat(Optional.absent().orNull()).isNull();
        assertThat(Preconditions.checkNotNull("checked", "message")).isEqualTo("checked");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> Preconditions.checkArgument(false, "bad %s", "argument"))
                .withMessage("bad argument");
    }

    @Test
    void immutableCollectionsAndOrderingPreserveExpectedStructure() {
        ImmutableList<String> names = ImmutableList.<String>builder()
                .add("beta")
                .add("alpha")
                .add("gamma")
                .build();
        Ordering<String> naturalOrdering = Ordering.natural();
        ImmutableList<String> sortedNames = naturalOrdering.immutableSortedCopy(names);
        ImmutableList<String> reversedNames = names.reverse();
        ImmutableSet<String> uniqueNames = ImmutableSet.copyOf(names);
        ImmutableMap<String, Integer> nameLengths = ImmutableMap.<String, Integer>builder()
                .put("alpha", 5)
                .put("beta", 4)
                .build();
        ImmutableBiMap<String, Integer> singleIndex = ImmutableBiMap.of("answer", 42);
        ImmutableListMultimap<String, String> aliases = ImmutableListMultimap.<String, String>builder()
                .put("language", "java")
                .put("language", "jvm")
                .put("format", "jar")
                .build();

        assertThat(sortedNames).containsExactly("alpha", "beta", "gamma");
        assertThat(reversedNames).containsExactly("gamma", "alpha", "beta");
        assertThat(uniqueNames).containsExactlyInAnyOrder("alpha", "beta", "gamma");
        assertThat(nameLengths).containsEntry("alpha", 5).containsEntry("beta", 4);
        assertThat(singleIndex.inverse()).containsEntry(42, "answer");
        assertThat(aliases.get("language")).containsExactly("java", "jvm");
        assertThat(aliases.asMap()).containsKeys("language", "format");
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> sortedNames.add("delta"));
    }

    @Test
    void collectionUtilitiesCreateLiveFilteredTransformedAndPartitionedViews() {
        List<String> words = Lists.newArrayList("alpha", "beta", "alpine", "gamma");
        Collection<String> aWords = Collections2.filter(words, Predicates.containsPattern("^a"));
        Collection<String> uppercased = Collections2.transform(aWords, word -> word.toUpperCase(Locale.ROOT));
        List<List<String>> partitions = Lists.partition(words, 2);

        assertThat(aWords).containsExactly("alpha", "alpine");
        assertThat(uppercased).containsExactly("ALPHA", "ALPINE");
        assertThat(partitions).containsExactly(
                Arrays.asList("alpha", "beta"),
                Arrays.asList("alpine", "gamma"));

        words.add("atlas");

        assertThat(aWords).containsExactly("alpha", "alpine", "atlas");
        assertThat(uppercased).containsExactly("ALPHA", "ALPINE", "ATLAS");
        assertThat(partitions).containsExactly(
                Arrays.asList("alpha", "beta"),
                Arrays.asList("alpine", "gamma"),
                Arrays.asList("atlas"));
        assertThat(Iterables.any(aWords, Predicates.equalTo("atlas"))).isTrue();
        assertThat(Iterables.all(aWords, Predicates.containsPattern("^a"))).isTrue();
    }

    @Test
    void mutableCollectionTypesTrackMultiplicityRelationsAndTables() {
        HashMultiset<String> multiset = HashMultiset.create(Arrays.asList("a", "b", "a", "c", "a"));
        HashMultimap<String, String> multimap = HashMultimap.create();
        HashBasedTable<String, String, Integer> table = HashBasedTable.create();

        multiset.add("b", 2);
        multiset.remove("a", 1);
        multiset.setCount("c", 3);
        multimap.put("letters", "a");
        multimap.put("letters", "b");
        multimap.put("letters", "a");
        multimap.put("digits", "1");
        table.put("service-a", "q1", 10);
        table.put("service-a", "q2", 20);
        table.put("service-b", "q1", 30);

        assertThat(multiset.count("a")).isEqualTo(2);
        assertThat(multiset.count("b")).isEqualTo(3);
        assertThat(multiset.count("c")).isEqualTo(3);
        assertThat(multiset.entrySet())
                .extracting(Multiset.Entry::getElement)
                .containsExactlyInAnyOrder("a", "b", "c");
        assertThat(multimap.get("letters")).containsExactlyInAnyOrder("a", "b");
        assertThat(multimap.keys().count("letters")).isEqualTo(2);
        assertThat(multimap.entries()).hasSize(3);
        assertThat(table.row("service-a")).containsEntry("q1", 10).containsEntry("q2", 20);
        assertThat(table.column("q1")).containsEntry("service-a", 10).containsEntry("service-b", 30);
        assertThat(table.cellSet()).extracting(Table.Cell::getValue).containsExactlyInAnyOrder(10, 20, 30);
    }

    @Test
    void mapAndSetViewsAreComputedFromBackingCollections() {
        Map<String, Integer> left = ImmutableMap.of("same", 1, "changed", 2, "left", 3);
        Map<String, Integer> right = ImmutableMap.of("same", 1, "changed", 20, "right", 4);
        MapDifference<String, Integer> difference = Maps.difference(left, right);
        Map<String, Integer> scores = Maps.newLinkedHashMap();
        scores.put("alpha", 1);
        scores.put("beta", 2);
        scores.put("atom", 3);

        Map<String, Integer> transformed = Maps.transformValues(scores, value -> value * 10);
        Map<String, Integer> filtered = Maps.filterKeys(scores, key -> key.startsWith("a"));

        assertThat(difference.entriesInCommon()).containsEntry("same", 1);
        assertThat(difference.entriesOnlyOnLeft()).containsEntry("left", 3);
        assertThat(difference.entriesOnlyOnRight()).containsEntry("right", 4);
        assertThat(difference.entriesDiffering().get("changed").leftValue()).isEqualTo(2);
        assertThat(difference.entriesDiffering().get("changed").rightValue()).isEqualTo(20);
        assertThat(transformed).containsEntry("alpha", 10).containsEntry("beta", 20).containsEntry("atom", 30);
        assertThat(filtered).containsOnlyKeys("alpha", "atom");
        scores.put("alpine", 4);
        assertThat(transformed).containsEntry("alpine", 40);
        assertThat(filtered).containsKey("alpine");
        assertThat(Sets.union(ImmutableSet.of("a"), ImmutableSet.of("b"))).containsExactlyInAnyOrder("a", "b");
        assertThat(Sets.intersection(
                ImmutableSet.copyOf(Arrays.asList("a", "b")),
                ImmutableSet.copyOf(Arrays.asList("b", "c"))))
                .containsExactly("b");
        assertThat(Sets.difference(ImmutableSet.copyOf(Arrays.asList("a", "b")), ImmutableSet.of("b")))
                .containsExactly("a");
    }

    @Test
    void cachesLoadExpireNotifyAndRecordStatsWithDeterministicTicker() {
        ManualTicker ticker = new ManualTicker();
        AtomicReference<RemovalNotification<String, Integer>> removed = new AtomicReference<>();
        LoadingCache<String, Integer> cache = CacheBuilder.newBuilder()
                .maximumSize(10)
                .expireAfterWrite(5, TimeUnit.SECONDS)
                .ticker(ticker)
                .recordStats()
                .removalListener(removed::set)
                .build(CacheLoader.from((String key) -> key.length()));

        assertThat(cache.getUnchecked("alpha")).isEqualTo(5);
        assertThat(cache.getUnchecked("alpha")).isEqualTo(5);
        assertThat(cache.stats().missCount()).isEqualTo(1);
        assertThat(cache.stats().hitCount()).isEqualTo(1);

        ticker.advance(6, TimeUnit.SECONDS);
        assertThat(cache.getIfPresent("alpha")).isNull();
        cache.cleanUp();

        assertThat(removed.get()).isNotNull();
        assertThat(removed.get().getKey()).isEqualTo("alpha");
        assertThat(removed.get().getValue()).isEqualTo(5);
    }

    @Test
    void manualCachesSupportCallableLoadingBulkLookupAndInvalidation() throws Exception {
        Cache<String, Integer> cache = CacheBuilder.newBuilder()
                .maximumSize(10)
                .recordStats()
                .build();

        cache.put("one", 1);
        cache.putAll(ImmutableMap.of("two", 2, "three", 3));

        assertThat(cache.getIfPresent("one")).isEqualTo(1);
        assertThat(cache.get("four", () -> 4)).isEqualTo(4);
        assertThat(cache.getAllPresent(Arrays.asList("one", "missing", "four")))
                .containsEntry("one", 1)
                .containsEntry("four", 4)
                .doesNotContainKey("missing");
        assertThat(cache.asMap()).containsEntry("two", 2);
        cache.invalidate("two");
        assertThat(cache.getIfPresent("two")).isNull();
        cache.invalidateAll(Arrays.asList("one", "three", "four"));
        assertThat(cache.size()).isZero();
    }

    @Test
    void byteStreamsReadPrimitiveValuesFromByteArraysAndStreams() {
        byte[] payload = {
                0x01, 0x02, 0x03, 0x04,
                0x05, 0x06,
                0x11, 0x12, 0x13, 0x14
        };

        ByteArrayDataInput fromStart = ByteStreams.newDataInput(payload);
        ByteArrayDataInput fromOffset = ByteStreams.newDataInput(payload, 6);
        ByteArrayDataInput fromStream = ByteStreams.newDataInput(new ByteArrayInputStream(payload));

        assertThat(fromStart.readInt()).isEqualTo(0x01020304);
        assertThat(fromStart.readShort()).isEqualTo((short) 0x0506);
        assertThat(fromOffset.readInt()).isEqualTo(0x11121314);
        assertThat(fromStream.readInt()).isEqualTo(0x01020304);
    }

    @Test
    void inetAddressAndPrimitiveHelpersConvertWithoutNameServiceLookup() throws Exception {
        InetAddress loopback = InetAddresses.forString("127.0.0.1");
        InetAddress ipv6 = InetAddresses.forString("2001:db8::1");
        Inet4Address fromInteger = InetAddresses.fromInteger(0x7f000001);

        assertThat(InetAddresses.isInetAddress("2001:db8::1")).isTrue();
        assertThat(InetAddresses.isInetAddress("not an address")).isFalse();
        assertThat(InetAddresses.toAddrString(ipv6)).isEqualTo("2001:db8::1");
        assertThat(InetAddresses.toUriString(ipv6)).isEqualTo("[2001:db8::1]");
        assertThat(InetAddresses.increment(loopback).getHostAddress()).isEqualTo("127.0.0.2");
        assertThat(InetAddresses.decrement(InetAddresses.forString("127.0.0.2")).getHostAddress())
                .isEqualTo("127.0.0.1");
        assertThat(fromInteger.getHostAddress()).isEqualTo("127.0.0.1");
        assertThat(Ints.toByteArray(0x01020304)).containsExactly((byte) 1, (byte) 2, (byte) 3, (byte) 4);
        assertThat(Ints.fromBytes((byte) 1, (byte) 2, (byte) 3, (byte) 4)).isEqualTo(0x01020304);
        assertThat(Ints.saturatedCast(Long.MAX_VALUE)).isEqualTo(Integer.MAX_VALUE);
        assertThat(Ints.saturatedCast(Long.MIN_VALUE)).isEqualTo(Integer.MIN_VALUE);
        assertThat(UnsignedBytes.toInt((byte) 0xff)).isEqualTo(255);
    }

    @Test
    void listenableFuturesTransformFallbackAndInvokeCallbacks() throws Exception {
        AtomicReference<String> callbackValue = new AtomicReference<>();
        AtomicReference<Throwable> callbackFailure = new AtomicReference<>();
        SettableFuture<String> source = SettableFuture.create();
        ListenableFuture<Integer> transformed = Futures.transform(
                source,
                String::length,
                MoreExecutors.directExecutor());

        Futures.addCallback(transformed, new FutureCallback<Integer>() {
            @Override
            public void onSuccess(Integer value) {
                callbackValue.set("length=" + value);
            }

            @Override
            public void onFailure(Throwable throwable) {
                callbackFailure.set(throwable);
            }
        }, MoreExecutors.directExecutor());

        assertThat(source.set("jersey")).isTrue();
        assertThat(transformed.get(1, TimeUnit.SECONDS)).isEqualTo(6);
        assertThat(callbackValue.get()).isEqualTo("length=6");
        assertThat(callbackFailure.get()).isNull();

        ListenableFuture<String> recovered = Futures.withFallback(
                Futures.<String>immediateFailedFuture(new IllegalStateException("boom")),
                throwable -> Futures.immediateFuture("fallback"),
                MoreExecutors.directExecutor());
        ListenableFuture<java.util.List<String>> combined = Futures.allAsList(
                Futures.immediateFuture("first"),
                recovered);

        assertThat(recovered.get(1, TimeUnit.SECONDS)).isEqualTo("fallback");
        assertThat(combined.get(1, TimeUnit.SECONDS)).containsExactly("first", "fallback");
    }

    private static final class ManualTicker extends Ticker {
        private long nanos;

        @Override
        public long read() {
            return nanos;
        }

        void advance(long duration, TimeUnit unit) {
            nanos += unit.toNanos(duration);
        }
    }
}
