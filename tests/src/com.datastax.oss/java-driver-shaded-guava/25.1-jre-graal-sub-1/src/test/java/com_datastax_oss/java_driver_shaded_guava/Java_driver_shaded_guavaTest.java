/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_datastax_oss.java_driver_shaded_guava;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.datastax.oss.driver.shaded.guava.common.base.CaseFormat;
import com.datastax.oss.driver.shaded.guava.common.base.CharMatcher;
import com.datastax.oss.driver.shaded.guava.common.base.Joiner;
import com.datastax.oss.driver.shaded.guava.common.base.MoreObjects;
import com.datastax.oss.driver.shaded.guava.common.base.Splitter;
import com.datastax.oss.driver.shaded.guava.common.base.Supplier;
import com.datastax.oss.driver.shaded.guava.common.base.Suppliers;
import com.datastax.oss.driver.shaded.guava.common.cache.CacheBuilder;
import com.datastax.oss.driver.shaded.guava.common.cache.CacheLoader;
import com.datastax.oss.driver.shaded.guava.common.cache.LoadingCache;
import com.datastax.oss.driver.shaded.guava.common.collect.ArrayListMultimap;
import com.datastax.oss.driver.shaded.guava.common.collect.BiMap;
import com.datastax.oss.driver.shaded.guava.common.collect.HashBasedTable;
import com.datastax.oss.driver.shaded.guava.common.collect.HashBiMap;
import com.datastax.oss.driver.shaded.guava.common.collect.HashMultiset;
import com.datastax.oss.driver.shaded.guava.common.collect.ImmutableList;
import com.datastax.oss.driver.shaded.guava.common.collect.ImmutableMap;
import com.datastax.oss.driver.shaded.guava.common.collect.ImmutableSetMultimap;
import com.datastax.oss.driver.shaded.guava.common.collect.ImmutableTable;
import com.datastax.oss.driver.shaded.guava.common.collect.Iterables;
import com.datastax.oss.driver.shaded.guava.common.collect.Lists;
import com.datastax.oss.driver.shaded.guava.common.collect.Multimap;
import com.datastax.oss.driver.shaded.guava.common.collect.Multimaps;
import com.datastax.oss.driver.shaded.guava.common.collect.Range;
import com.datastax.oss.driver.shaded.guava.common.collect.RangeMap;
import com.datastax.oss.driver.shaded.guava.common.collect.RangeSet;
import com.datastax.oss.driver.shaded.guava.common.collect.Table;
import com.datastax.oss.driver.shaded.guava.common.collect.Tables;
import com.datastax.oss.driver.shaded.guava.common.collect.TreeRangeMap;
import com.datastax.oss.driver.shaded.guava.common.collect.TreeRangeSet;
import com.datastax.oss.driver.shaded.guava.common.escape.Escaper;
import com.datastax.oss.driver.shaded.guava.common.graph.EndpointPair;
import com.datastax.oss.driver.shaded.guava.common.graph.GraphBuilder;
import com.datastax.oss.driver.shaded.guava.common.graph.MutableGraph;
import com.datastax.oss.driver.shaded.guava.common.graph.MutableNetwork;
import com.datastax.oss.driver.shaded.guava.common.graph.MutableValueGraph;
import com.datastax.oss.driver.shaded.guava.common.graph.NetworkBuilder;
import com.datastax.oss.driver.shaded.guava.common.graph.ValueGraphBuilder;
import com.datastax.oss.driver.shaded.guava.common.hash.BloomFilter;
import com.datastax.oss.driver.shaded.guava.common.hash.Funnels;
import com.datastax.oss.driver.shaded.guava.common.hash.HashCode;
import com.datastax.oss.driver.shaded.guava.common.hash.Hashing;
import com.datastax.oss.driver.shaded.guava.common.html.HtmlEscapers;
import com.datastax.oss.driver.shaded.guava.common.io.BaseEncoding;
import com.datastax.oss.driver.shaded.guava.common.io.ByteSource;
import com.datastax.oss.driver.shaded.guava.common.io.CharSource;
import com.datastax.oss.driver.shaded.guava.common.io.CharStreams;
import com.datastax.oss.driver.shaded.guava.common.math.IntMath;
import com.datastax.oss.driver.shaded.guava.common.math.LongMath;
import com.datastax.oss.driver.shaded.guava.common.net.HostAndPort;
import com.datastax.oss.driver.shaded.guava.common.net.InternetDomainName;
import com.datastax.oss.driver.shaded.guava.common.net.MediaType;
import com.datastax.oss.driver.shaded.guava.common.net.UrlEscapers;
import com.datastax.oss.driver.shaded.guava.common.primitives.Doubles;
import com.datastax.oss.driver.shaded.guava.common.primitives.Ints;
import com.datastax.oss.driver.shaded.guava.common.primitives.UnsignedInteger;
import com.datastax.oss.driver.shaded.guava.common.util.concurrent.AbstractIdleService;
import com.datastax.oss.driver.shaded.guava.common.util.concurrent.Futures;
import com.datastax.oss.driver.shaded.guava.common.util.concurrent.ListenableFuture;
import com.datastax.oss.driver.shaded.guava.common.util.concurrent.MoreExecutors;
import com.datastax.oss.driver.shaded.guava.common.util.concurrent.RateLimiter;
import com.datastax.oss.driver.shaded.guava.common.util.concurrent.Service;
import com.datastax.oss.driver.shaded.guava.common.util.concurrent.SettableFuture;
import com.datastax.oss.driver.shaded.guava.common.util.concurrent.Striped;
import java.io.IOException;
import java.io.StringReader;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import org.junit.jupiter.api.Test;

public class Java_driver_shaded_guavaTest {
    @Test
    void baseUtilitiesComposeStringsPredicatesAndSuppliers() {
        List<String> parts = Splitter.on(',')
                .trimResults()
                .omitEmptyStrings()
                .splitToList(" alpha, beta,, gamma ");

        assertThat(parts).containsExactly("alpha", "beta", "gamma");
        assertThat(Joiner.on("|").useForNull("missing").join("alpha", null, "gamma"))
                .isEqualTo("alpha|missing|gamma");
        assertThat(CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_CAMEL, "driver-shaded-guava"))
                .isEqualTo("DriverShadedGuava");
        assertThat(CharMatcher.javaLetterOrDigit().or(CharMatcher.whitespace()).retainFrom("A-1 b_2"))
                .isEqualTo("A1 b2");
        assertThat(MoreObjects.toStringHelper("Coordinate")
                .add("group", "com.datastax.oss")
                .add("artifact", "java-driver-shaded-guava")
                .toString())
                .isEqualTo("Coordinate{group=com.datastax.oss, artifact=java-driver-shaded-guava}");

        AtomicInteger invocations = new AtomicInteger();
        Supplier<String> memoized = Suppliers.memoize(() -> "value-" + invocations.incrementAndGet());

        assertThat(memoized.get()).isEqualTo("value-1");
        assertThat(memoized.get()).isEqualTo("value-1");
        assertThat(invocations).hasValue(1);
    }

    @Test
    void collectionUtilitiesHandleImmutableMultimapsRangesTablesAndViews() {
        ImmutableList<String> names = ImmutableList.of("cassandra", "driver", "driver", "guava");
        HashMultiset<String> occurrences = HashMultiset.create(names);
        BiMap<String, Integer> idsByName = HashBiMap.create(ImmutableMap.of("alpha", 1, "beta", 2));
        ImmutableSetMultimap<Character, String> namesByInitial = names.stream()
                .distinct()
                .collect(ImmutableSetMultimap.toImmutableSetMultimap(name -> name.charAt(0), name -> name));

        assertThat(occurrences.count("driver")).isEqualTo(2);
        assertThat(idsByName.inverse().get(2)).isEqualTo("beta");
        assertThat(namesByInitial.get('d')).containsExactly("driver");

        RangeSet<Integer> coveredPartitions = TreeRangeSet.create();
        coveredPartitions.add(Range.closedOpen(0, 4));
        coveredPartitions.add(Range.closed(8, 10));
        RangeMap<Integer, String> routing = TreeRangeMap.create();
        routing.put(Range.closedOpen(0, 4), "local");
        routing.put(Range.closed(8, 10), "remote");

        assertThat(coveredPartitions.contains(3)).isTrue();
        assertThat(coveredPartitions.contains(5)).isFalse();
        assertThat(routing.get(9)).isEqualTo("remote");
        assertThat(coveredPartitions.asRanges()).containsExactly(Range.closedOpen(0, 4), Range.closed(8, 10));

        Table<String, String, Integer> table = HashBasedTable.create();
        table.put("row1", "col1", 11);
        table.put("row1", "col2", 12);
        Table<String, String, Integer> transpose = Tables.transpose(table);
        ImmutableTable<String, String, Integer> immutableTable = ImmutableTable.copyOf(table);

        assertThat(transpose.get("col2", "row1")).isEqualTo(12);
        assertThat(immutableTable.row("row1")).containsEntry("col1", 11);
        assertThat(Lists.reverse(names)).containsExactly("guava", "driver", "driver", "cassandra");
    }

    @Test
    void multimapsAndGraphsExposeConsistentDerivedViews() {
        ArrayListMultimap<String, Integer> raw = ArrayListMultimap.create();
        raw.put("odd", 1);
        raw.put("odd", 3);
        raw.put("even", 2);
        Multimap<String, Integer> squared = Multimaps.transformValues(raw, value -> value * value);
        Map<String, List<Integer>> asMap = Multimaps.asMap(raw);

        assertThat(squared.get("odd")).containsExactly(1, 9);
        assertThat(asMap.get("even")).containsExactly(2);
        assertThat(Iterables.getOnlyElement(squared.get("even"))).isEqualTo(4);

        MutableGraph<String> graph = GraphBuilder.directed().<String>build();
        graph.putEdge("contact-point", "control-connection");
        graph.putEdge("control-connection", "session");

        assertThat(graph.successors("contact-point")).containsExactly("control-connection");
        assertThat(graph.predecessors("session")).containsExactly("control-connection");
        assertThat(graph.edges()).contains(EndpointPair.ordered("contact-point", "control-connection"));

        MutableValueGraph<String, Integer> weightedGraph = ValueGraphBuilder.undirected().<String, Integer>build();
        weightedGraph.putEdgeValue("node-a", "node-b", 7);

        assertThat(weightedGraph.edgeValueOrDefault("node-a", "node-b", -1)).isEqualTo(7);
        assertThat(weightedGraph.edgeValueOrDefault("node-a", "node-c", -1)).isEqualTo(-1);

        MutableNetwork<String, String> network = NetworkBuilder.directed()
                .allowsParallelEdges(true)
                .<String, String>build();
        network.addEdge("producer", "consumer", "stream-1");
        network.addEdge("producer", "consumer", "stream-2");

        assertThat(network.edgesConnecting("producer", "consumer")).containsExactlyInAnyOrder("stream-1", "stream-2");
        assertThat(network.incidentNodes("stream-1")).isEqualTo(EndpointPair.ordered("producer", "consumer"));
    }

    @Test
    void cachesLoadInvalidateAndReportStatistics() {
        AtomicInteger loadCount = new AtomicInteger();
        LoadingCache<String, Integer> cache = CacheBuilder.newBuilder()
                .maximumSize(10)
                .recordStats()
                .build(new CacheLoader<String, Integer>() {
                    @Override
                    public Integer load(String key) {
                        return key.length() + loadCount.incrementAndGet();
                    }
                });

        assertThat(cache.getUnchecked("alpha")).isEqualTo(6);
        assertThat(cache.getUnchecked("alpha")).isEqualTo(6);
        assertThat(cache.stats().hitCount()).isEqualTo(1);
        assertThat(cache.stats().missCount()).isEqualTo(1);

        cache.invalidate("alpha");

        assertThat(cache.getUnchecked("alpha")).isEqualTo(7);
        assertThat(loadCount).hasValue(2);
        assertThat(cache.asMap()).containsEntry("alpha", 7);
    }

    @Test
    void futuresTransformRecoverAndNotifyListeners()
            throws ExecutionException, InterruptedException, TimeoutException {
        SettableFuture<String> source = SettableFuture.create();
        List<String> notifications = new ArrayList<>();
        ListenableFuture<Integer> transformed = Futures.transform(
                source,
                value -> value.length(),
                MoreExecutors.directExecutor());
        transformed.addListener(() -> notifications.add("done"), MoreExecutors.directExecutor());

        assertThat(source.set("native-image")).isTrue();
        assertThat(transformed.get(1, TimeUnit.SECONDS)).isEqualTo(12);
        assertThat(notifications).containsExactly("done");

        ListenableFuture<String> recovered = Futures.catching(
                Futures.immediateFailedFuture(new IllegalArgumentException("bad input")),
                IllegalArgumentException.class,
                exception -> "recovered: " + exception.getMessage(),
                MoreExecutors.directExecutor());

        assertThat(recovered.get(1, TimeUnit.SECONDS)).isEqualTo("recovered: bad input");
    }

    @Test
    void concurrencyHelpersRateLimitAndProvideKeyedSynchronization() {
        RateLimiter limiter = RateLimiter.create(1_000_000.0d);

        assertThat(limiter.getRate()).isEqualTo(1_000_000.0d);
        assertThat(limiter.tryAcquire()).isTrue();

        limiter.setRate(2_000_000.0d);

        assertThat(limiter.getRate()).isEqualTo(2_000_000.0d);

        Striped<Lock> stripedLocks = Striped.lock(4);
        Lock sessionLock = stripedLocks.get("session-a");
        Lock sameSessionLock = stripedLocks.get("session-a");

        assertThat(stripedLocks.size()).isEqualTo(4);
        assertThat(sessionLock).isSameAs(sameSessionLock);

        sessionLock.lock();
        boolean reacquired = sameSessionLock.tryLock();
        try {
            assertThat(reacquired).isTrue();
        } finally {
            if (reacquired) {
                sameSessionLock.unlock();
            }
            sessionLock.unlock();
        }

        assertThat(stripedLocks.bulkGet(ImmutableList.of("session-b", "session-a", "session-b")))
                .hasSize(3)
                .contains(stripedLocks.get("session-a"), stripedLocks.get("session-b"));
    }

    @Test
    void servicesManageLifecycleAndNotifyListeners() throws InterruptedException, TimeoutException {
        RecordingIdleService service = new RecordingIdleService();
        List<Service.State> observedStates = new CopyOnWriteArrayList<>();
        CountDownLatch terminated = new CountDownLatch(1);

        service.addListener(new Service.Listener() {
            @Override
            public void running() {
                observedStates.add(Service.State.RUNNING);
            }

            @Override
            public void terminated(Service.State from) {
                observedStates.add(Service.State.TERMINATED);
                terminated.countDown();
            }
        }, MoreExecutors.directExecutor());

        service.startAsync().awaitRunning(1, TimeUnit.SECONDS);

        assertThat(service.isRunning()).isTrue();
        assertThat(service.state()).isEqualTo(Service.State.RUNNING);
        assertThat(service.startCount).hasValue(1);

        service.stopAsync().awaitTerminated(1, TimeUnit.SECONDS);

        assertThat(terminated.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(service.state()).isEqualTo(Service.State.TERMINATED);
        assertThat(service.stopCount).hasValue(1);
        assertThat(observedStates).containsExactly(Service.State.RUNNING, Service.State.TERMINATED);
    }

    @Test
    void hashingBloomFiltersAndEncodingProcessInMemoryData() throws IOException {
        ByteSource bytes = ByteSource.wrap("driver shaded guava".getBytes(UTF_8));
        CharSource chars = bytes.asCharSource(UTF_8);
        HashCode firstHash = Hashing.sha256().hashString("driver shaded guava", UTF_8);
        HashCode secondHash = bytes.hash(Hashing.sha256());
        BloomFilter<String> bloomFilter = BloomFilter.create(Funnels.stringFunnel(UTF_8), 32);

        assertThat(chars.read()).isEqualTo("driver shaded guava");
        assertThat(chars.length()).isEqualTo(19);
        assertThat(CharStreams.toString(new StringReader("line-1\nline-2"))).isEqualTo("line-1\nline-2");
        assertThat(firstHash).isEqualTo(secondHash);
        assertThat(BaseEncoding.base16().lowerCase().encode(new byte[] {0x0a, 0x0f, 0x10})).isEqualTo("0a0f10");
        assertThat(bloomFilter.put("alpha")).isTrue();
        assertThat(bloomFilter.mightContain("alpha")).isTrue();
    }

    @Test
    void netEscapingMathAndPrimitiveHelpersUseShadedPublicApi() {
        HostAndPort contactPoint = HostAndPort.fromString("[2001:db8::1]:9042");
        InternetDomainName domainName = InternetDomainName.from("forums.bbc.co.uk");
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        Escaper htmlEscaper = HtmlEscapers.htmlEscaper();
        Escaper formEscaper = UrlEscapers.urlFormParameterEscaper();
        UnsignedInteger maxUnsigned = UnsignedInteger.fromIntBits(-1);

        assertThat(contactPoint.getHost()).isEqualTo("2001:db8::1");
        assertThat(contactPoint.getPort()).isEqualTo(9042);
        assertThat(domainName.topPrivateDomain().toString()).isEqualTo("bbc.co.uk");
        assertThat(mediaType.charset().get()).isEqualTo(UTF_8);
        assertThat(mediaType.withoutParameters()).isEqualTo(MediaType.JSON_UTF_8.withoutParameters());
        assertThat(htmlEscaper.escape("<driver>&'\"")).isEqualTo("&lt;driver&gt;&amp;&#39;&quot;");
        assertThat(formEscaper.escape("a b+c")).isEqualTo("a+b%2Bc");
        assertThat(Ints.asList(1, 2, 3)).containsExactly(1, 2, 3);
        assertThat(Doubles.tryParse("6.25")).isEqualTo(6.25d);
        assertThat(maxUnsigned.longValue()).isEqualTo(4_294_967_295L);
        assertThat(IntMath.divide(7, 3, RoundingMode.CEILING)).isEqualTo(3);
        assertThat(LongMath.binomial(6, 2)).isEqualTo(15L);
    }

    @Test
    void invalidInputsFailWithDocumentedExceptions() {
        assertThatThrownBy(() -> Splitter.fixedLength(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HostAndPort.fromString("localhost:not-a-port"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Ints.checkedCast(Long.MAX_VALUE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MediaType.parse("not a media type"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static final class RecordingIdleService extends AbstractIdleService {
        private final AtomicInteger startCount = new AtomicInteger();
        private final AtomicInteger stopCount = new AtomicInteger();

        @Override
        protected void startUp() {
            startCount.incrementAndGet();
        }

        @Override
        protected void shutDown() {
            stopCount.incrementAndGet();
        }
    }
}
