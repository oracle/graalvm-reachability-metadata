/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_graphql_java.java_dataloader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

import org.dataloader.BatchLoader;
import org.dataloader.BatchLoaderEnvironment;
import org.dataloader.BatchLoaderWithContext;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderFactory;
import org.dataloader.DataLoaderOptions;
import org.dataloader.DataLoaderRegistry;
import org.dataloader.DispatchResult;
import org.dataloader.MappedBatchLoader;
import org.dataloader.Try;
import org.dataloader.stats.SimpleStatisticsCollector;
import org.dataloader.stats.Statistics;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Java_dataloaderTest {

    @Test
    void batchesRequestsCachesDuplicateLoadsAndSupportsPrimeAndClear() {
        List<List<Integer>> dispatchedBatches = new ArrayList<>();
        BatchLoader<Integer, String> batchLoader = keys -> {
            dispatchedBatches.add(List.copyOf(keys));
            return CompletableFuture.completedFuture(keys.stream()
                .map(key -> "value-" + key)
                .toList());
        };

        DataLoaderOptions options = DataLoaderOptions.newOptions()
            .setMaxBatchSize(2)
            .setStatisticsCollector(SimpleStatisticsCollector::new);
        DataLoader<Integer, String> dataLoader = DataLoaderFactory.newDataLoader(batchLoader, options);

        dataLoader.prime(99, "primed-value");
        CompletableFuture<String> primed = dataLoader.load(99);
        assertThat(primed.join()).isEqualTo("primed-value");
        assertThat(dispatchedBatches).isEmpty();
        assertThat(dataLoader.getIfPresent(99)).contains(primed);
        assertThat(dataLoader.getIfCompleted(99)).contains(primed);

        dataLoader.clear(99);

        CompletableFuture<String> first = dataLoader.load(1);
        CompletableFuture<String> second = dataLoader.load(2);
        CompletableFuture<String> third = dataLoader.load(3);
        CompletableFuture<String> duplicateSecond = dataLoader.load(2);

        assertThat(duplicateSecond).isSameAs(second);
        assertThat(dataLoader.getIfPresent(1)).contains(first);
        assertThat(dataLoader.getIfCompleted(1)).isEmpty();

        DispatchResult<String> dispatchResult = dataLoader.dispatchWithCounts();
        assertThat(dispatchResult.getKeysCount()).isEqualTo(3);
        assertThat(dispatchResult.getPromisedResults().join()).containsExactly("value-1", "value-2", "value-3");
        assertThat(first.join()).isEqualTo("value-1");
        assertThat(second.join()).isEqualTo("value-2");
        assertThat(third.join()).isEqualTo("value-3");
        assertThat(dispatchedBatches).containsExactly(List.of(1, 2), List.of(3));
        assertThat(dataLoader.getIfCompleted(1)).contains(first);

        dataLoader.clear(2);
        CompletableFuture<String> reloadedSecond = dataLoader.load(2);
        assertThat(dataLoader.dispatchAndJoin()).containsExactly("value-2");
        assertThat(reloadedSecond.join()).isEqualTo("value-2");
        assertThat(dispatchedBatches).containsExactly(List.of(1, 2), List.of(3), List.of(2));

        Statistics statistics = dataLoader.getStatistics();
        assertThat(statistics.getLoadCount()).isEqualTo(6);
        assertThat(statistics.getBatchInvokeCount()).isEqualTo(3);
        assertThat(statistics.getBatchLoadCount()).isEqualTo(4);
        assertThat(statistics.getCacheHitCount()).isGreaterThanOrEqualTo(2);
        assertThat(statistics.toMap()).containsKeys("loadCount", "batchInvokeCount", "cacheHitCount");
    }

    @Test
    void batchLoaderWithContextReceivesLoaderAndPerKeyContexts() {
        AtomicReference<Object> batchContext = new AtomicReference<>();
        AtomicReference<Map<Object, Object>> keyContexts = new AtomicReference<>();
        AtomicReference<List<Object>> keyContextsList = new AtomicReference<>();
        BatchLoaderWithContext<String, String> batchLoader = (keys, environment) -> {
            batchContext.set(environment.getContext());
            keyContexts.set(new LinkedHashMap<>(environment.getKeyContexts()));
            keyContextsList.set(List.copyOf(environment.getKeyContextsList()));
            return CompletableFuture.completedFuture(keys.stream()
                .map(key -> environment.<String>getContext() + ":" + environment.getKeyContexts().get(key))
                .toList());
        };

        DataLoaderOptions options = DataLoaderOptions.newOptions()
            .setBatchLoaderContextProvider(() -> "tenant-a");
        DataLoader<String, String> dataLoader = DataLoaderFactory.newDataLoader(batchLoader, options);

        CompletableFuture<String> alpha = dataLoader.load("alpha", "ctx-1");
        CompletableFuture<String> beta = dataLoader.load("beta", "ctx-2");

        assertThat(dataLoader.dispatchAndJoin()).containsExactly("tenant-a:ctx-1", "tenant-a:ctx-2");
        assertThat(alpha.join()).isEqualTo("tenant-a:ctx-1");
        assertThat(beta.join()).isEqualTo("tenant-a:ctx-2");
        assertThat(batchContext.get()).isEqualTo("tenant-a");
        assertThat(keyContexts.get())
            .containsEntry("alpha", "ctx-1")
            .containsEntry("beta", "ctx-2");
        assertThat(keyContextsList.get()).containsExactly("ctx-1", "ctx-2");
    }

    @Test
    void mappedBatchLoaderResolvesMissingValuesAsNullAndSupportsLoadMany() {
        AtomicReference<Set<String>> requestedKeys = new AtomicReference<>();
        MappedBatchLoader<String, Integer> mappedBatchLoader = keys -> {
            requestedKeys.set(Set.copyOf(keys));
            Map<String, Integer> lengths = new LinkedHashMap<>();
            for (String key : keys) {
                if (!"missing".equals(key)) {
                    lengths.put(key, key.length());
                }
            }
            return CompletableFuture.completedFuture(lengths);
        };

        DataLoader<String, Integer> dataLoader = DataLoaderFactory.newMappedDataLoader(mappedBatchLoader);

        CompletableFuture<List<Integer>> many = dataLoader.loadMany(List.of("lion", "missing", "ox"));

        assertThat(dataLoader.dispatchAndJoin()).containsExactly(4, null, 2);
        assertThat(many.join()).containsExactly(4, null, 2);
        assertThat(requestedKeys.get()).containsExactlyInAnyOrder("lion", "missing", "ox");
    }

    @Test
    void tryBasedLoaderCompletesSuccessfulKeysAndSurfacesPerKeyFailures() {
        BatchLoader<String, Try<String>> batchLoader = keys -> CompletableFuture.completedFuture(keys.stream()
            .map(key -> "bad".equals(key)
                ? Try.<String>failed(new IllegalStateException("boom-" + key))
                : Try.succeeded(key.toUpperCase()))
            .toList());

        DataLoader<String, String> dataLoader = DataLoaderFactory.newDataLoaderWithTry(batchLoader);

        CompletableFuture<String> ok = dataLoader.load("ok");
        CompletableFuture<String> bad = dataLoader.load("bad");

        dataLoader.dispatch();

        assertThat(ok.join()).isEqualTo("OK");
        assertThatThrownBy(bad::join)
            .isInstanceOf(CompletionException.class)
            .hasCauseInstanceOf(IllegalStateException.class)
            .hasRootCauseMessage("boom-bad");
        assertThat(dataLoader.getIfCompleted("ok")).contains(ok);
        assertThat(dataLoader.getIfCompleted("bad")).contains(bad);
    }

    @Test
    void registryDispatchesAllLoadersAndAggregatesStatistics() {
        DataLoaderOptions options = DataLoaderOptions.newOptions()
            .setStatisticsCollector(SimpleStatisticsCollector::new);
        DataLoader<Integer, String> numbers = DataLoaderFactory.newDataLoader(keys -> CompletableFuture.completedFuture(keys.stream()
            .map(key -> "n" + key)
            .toList()), options);
        DataLoader<String, String> letters = DataLoaderFactory.newDataLoader(keys -> CompletableFuture.completedFuture(keys.stream()
            .map(String::toUpperCase)
            .toList()), options);

        DataLoaderRegistry registry = new DataLoaderRegistry()
            .register("numbers", numbers)
            .register("letters", letters);

        CompletableFuture<String> number = registry.<Integer, String>getDataLoader("numbers").load(7);
        CompletableFuture<String> firstLetters = registry.<String, String>getDataLoader("letters").load("ab");
        CompletableFuture<String> secondLetters = registry.<String, String>getDataLoader("letters").load("cd");

        assertThat(registry.getKeys()).containsExactlyInAnyOrder("numbers", "letters");
        assertThat(registry.dispatchDepth()).isEqualTo(3);
        assertThat(registry.dispatchAllWithCount()).isEqualTo(3);
        assertThat(number.join()).isEqualTo("n7");
        assertThat(firstLetters.join()).isEqualTo("AB");
        assertThat(secondLetters.join()).isEqualTo("CD");
        assertThat(registry.dispatchDepth()).isZero();

        Statistics statistics = registry.getStatistics();
        assertThat(statistics.getLoadCount()).isEqualTo(3);
        assertThat(statistics.getBatchInvokeCount()).isEqualTo(2);
        assertThat(statistics.getBatchLoadCount()).isEqualTo(3);
        assertThat(statistics.getCacheHitCount()).isZero();
    }
}
