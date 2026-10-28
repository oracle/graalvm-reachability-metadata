/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ben_manes_caffeine.caffeine;

import io.reactivex.rxjava3.subjects.PublishSubject;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;

@SuppressWarnings("ResultOfMethodCallIgnored")
public final class WriteBehindCacheWriter<K, V> {
    private final PublishSubject<Entry<K, V>> subject;

    private WriteBehindCacheWriter(Builder<K, V> builder) {
        subject = PublishSubject.create();
        subject.buffer(builder.bufferTimeNanos, TimeUnit.NANOSECONDS)
                .map(entries -> entries.stream().collect(toMap(Entry::getKey, Entry::getValue, builder.coalescer)))
                .subscribe(builder.writeAction::accept);
    }

    public void write(K key, V value) {
        subject.onNext(new SimpleImmutableEntry<>(key, value));
    }

    public static final class Builder<K, V> {
        private Consumer<Map<K, V>> writeAction;
        private BinaryOperator<V> coalescer;
        private long bufferTimeNanos;

        public Builder<K, V> bufferTime(long duration, TimeUnit unit) {
            this.bufferTimeNanos = TimeUnit.NANOSECONDS.convert(duration, unit);
            return this;
        }

        public Builder<K, V> writeAction(Consumer<Map<K, V>> writeAction) {
            this.writeAction = requireNonNull(writeAction);
            return this;
        }

        public Builder<K, V> coalesce(BinaryOperator<V> coalescer) {
            this.coalescer = requireNonNull(coalescer);
            return this;
        }

        public WriteBehindCacheWriter<K, V> build() {
            return new WriteBehindCacheWriter<>(this);
        }
    }
}
