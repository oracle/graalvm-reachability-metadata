/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ben_manes_caffeine.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BinaryOperator;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

public class ComputeTest {
    @Test
    public void givenCacheUpdate_writeBehindIsCalled() {
        AtomicBoolean writerCalled = new AtomicBoolean(false);
        var writer = new WriteBehindCacheWriter.Builder<Long, ZonedDateTime>()
                .bufferTime(1, TimeUnit.SECONDS)
                .coalesce(BinaryOperator.maxBy(ZonedDateTime::compareTo))
                .writeAction(entries -> writerCalled.set(true))
                .build();
        Cache<Long, ZonedDateTime> cache = Caffeine.newBuilder().build();
        cache.asMap().computeIfAbsent(1L, key -> {
            var value = ZonedDateTime.now();
            writer.write(key, value);
            return value;
        });
        Awaitility.await().untilTrue(writerCalled);
    }

    @Test
    public void givenCacheUpdateOnMultipleKeys_writeBehindIsCalled() {
        AtomicBoolean writerCalled = new AtomicBoolean(false);
        AtomicInteger numberOfEntries = new AtomicInteger(0);
        var writer = new WriteBehindCacheWriter.Builder<Long, ZonedDateTime>()
                .bufferTime(1, TimeUnit.SECONDS)
                .coalesce(BinaryOperator.maxBy(ZonedDateTime::compareTo))
                .writeAction(entries -> {
                    numberOfEntries.set(entries.size());
                    writerCalled.set(true);
                }).build();
        Cache<Long, ZonedDateTime> cache = Caffeine.newBuilder().build();
        LongStream.rangeClosed(1L, 3L).forEach(i -> cache.asMap().computeIfAbsent(i, key -> {
            var value = ZonedDateTime.now();
            writer.write(key, value);
            return value;
        }));
        Awaitility.await().untilTrue(writerCalled);
        assertThat(numberOfEntries.intValue()).isEqualTo(3);
    }

    @Test
    public void givenMultipleCacheUpdatesOnSameKey_writeBehindIsCalledWithMostRecentTime() {
        AtomicBoolean writerCalled = new AtomicBoolean(false);
        AtomicInteger numberOfEntries = new AtomicInteger(0);
        AtomicReference<ZonedDateTime> timeInWriteBehind = new AtomicReference<>();
        var writer = new WriteBehindCacheWriter.Builder<Long, ZonedDateTime>()
                .bufferTime(1, TimeUnit.SECONDS)
                .coalesce(BinaryOperator.maxBy(ZonedDateTime::compareTo))
                .writeAction(entries -> {
                    if (entries.isEmpty()) {
                        return;
                    }
                    numberOfEntries.set(entries.size());
                    ZonedDateTime zonedDateTime = entries.values().iterator().next();
                    timeInWriteBehind.set(zonedDateTime);
                    writerCalled.set(true);
                }).build();
        Cache<Long, ZonedDateTime> cache = Caffeine.newBuilder().build();
        var values = List.of(
                ZonedDateTime.of(2016, 6, 26, 8, 0, 0, 0, ZoneId.systemDefault()),
                ZonedDateTime.of(2016, 6, 26, 8, 0, 0, 100, ZoneId.systemDefault()),
                ZonedDateTime.of(2016, 6, 26, 8, 0, 0, 300, ZoneId.systemDefault()),
                ZonedDateTime.of(2016, 6, 26, 8, 0, 0, 500, ZoneId.systemDefault()));
        values.forEach(value -> cache.asMap().compute(1L, (key, oldValue) -> {
            writer.write(key, value);
            return value;
        }));
        var mostRecentTime = values.get(values.size() - 1);
        Awaitility.await().untilTrue(writerCalled);
        assertThat(numberOfEntries.intValue()).isEqualTo(1);
        assertThat(timeInWriteBehind.get()).isEqualTo(mostRecentTime);
    }
}
