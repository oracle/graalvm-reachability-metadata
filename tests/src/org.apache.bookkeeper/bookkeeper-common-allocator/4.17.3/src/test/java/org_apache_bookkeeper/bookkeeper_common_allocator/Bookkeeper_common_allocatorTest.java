/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_bookkeeper.bookkeeper_common_allocator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.netty.buffer.AbstractByteBufAllocator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.util.ResourceLeakDetector;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.bookkeeper.common.allocator.ByteBufAllocatorBuilder;
import org.apache.bookkeeper.common.allocator.ByteBufAllocatorWithOomHandler;
import org.apache.bookkeeper.common.allocator.LeakDetectionPolicy;
import org.apache.bookkeeper.common.allocator.OutOfMemoryPolicy;
import org.apache.bookkeeper.common.allocator.PoolingPolicy;
import org.junit.jupiter.api.Test;

public class Bookkeeper_common_allocatorTest {
    @Test
    void parsesLeakDetectionPolicyNamesCaseInsensitivelyAndDefaultsUnknownValues() {
        assertThat(LeakDetectionPolicy.values())
                .containsExactly(
                        LeakDetectionPolicy.Disabled,
                        LeakDetectionPolicy.Simple,
                        LeakDetectionPolicy.Advanced,
                        LeakDetectionPolicy.Paranoid);

        assertThat(LeakDetectionPolicy.parseLevel(" disabled ")).isEqualTo(LeakDetectionPolicy.Disabled);
        assertThat(LeakDetectionPolicy.parseLevel("SIMPLE")).isEqualTo(LeakDetectionPolicy.Simple);
        assertThat(LeakDetectionPolicy.parseLevel("advanced")).isEqualTo(LeakDetectionPolicy.Advanced);
        assertThat(LeakDetectionPolicy.parseLevel("pArAnOiD")).isEqualTo(LeakDetectionPolicy.Paranoid);
        assertThat(LeakDetectionPolicy.parseLevel("not-a-level")).isEqualTo(LeakDetectionPolicy.Disabled);
    }

    @Test
    void leakDetectionPolicyUpdatesNettyResourceLeakDetectorLevel() {
        ResourceLeakDetector.Level previousLevel = ResourceLeakDetector.getLevel();
        try {
            buildAllocatorWithLeakPolicy(LeakDetectionPolicy.Disabled);
            assertThat(ResourceLeakDetector.getLevel()).isEqualTo(ResourceLeakDetector.Level.DISABLED);

            buildAllocatorWithLeakPolicy(LeakDetectionPolicy.Simple);
            assertThat(ResourceLeakDetector.getLevel()).isEqualTo(ResourceLeakDetector.Level.SIMPLE);

            buildAllocatorWithLeakPolicy(LeakDetectionPolicy.Advanced);
            assertThat(ResourceLeakDetector.getLevel()).isEqualTo(ResourceLeakDetector.Level.ADVANCED);

            buildAllocatorWithLeakPolicy(LeakDetectionPolicy.Paranoid);
            assertThat(ResourceLeakDetector.getLevel()).isEqualTo(ResourceLeakDetector.Level.PARANOID);
        } finally {
            ResourceLeakDetector.setLevel(previousLevel);
        }
    }

    @Test
    void defaultBuilderCreatesPooledDirectAllocatorThatAllocatesUsableDirectBuffers() {
        ByteBufAllocatorWithOomHandler allocator = ByteBufAllocatorBuilder.create().build();

        ByteBuf buffer = allocator.buffer(16, 64);
        try {
            assertThat(allocator.isDirectBufferPooled()).isTrue();
            assertThat(buffer.isDirect()).isTrue();
            assertThat(buffer.capacity()).isGreaterThanOrEqualTo(16);
            assertThat(buffer.maxCapacity()).isEqualTo(64);

            buffer.writeInt(0xCAFE_BABE);
            buffer.writeByte(7);

            assertThat(buffer.readInt()).isEqualTo(0xCAFE_BABE);
            assertThat(buffer.readByte()).isEqualTo((byte) 7);
        } finally {
            buffer.release();
        }
    }

    @Test
    void unpooledHeapPolicyMakesGenericBuffersHeapBacked() {
        ByteBufAllocatorWithOomHandler allocator = ByteBufAllocatorBuilder.create()
                .poolingPolicy(PoolingPolicy.UnpooledHeap)
                .build();

        ByteBuf buffer = allocator.buffer(8, 32);
        ByteBuf directBuffer = allocator.directBuffer(8, 32);
        try {
            assertThat(allocator.isDirectBufferPooled()).isFalse();
            assertThat(buffer.isDirect()).isFalse();
            assertThat(directBuffer.isDirect()).isTrue();

            buffer.writeLong(42L);
            assertThat(buffer.readLong()).isEqualTo(42L);
        } finally {
            directBuffer.release();
            buffer.release();
        }
    }

    @Test
    void pooledDirectPolicyUsesPooledAllocatorForExplicitHeapBuffers() {
        CountingAllocator pooledAllocator = new CountingAllocator(true);
        CountingAllocator unpooledAllocator = new CountingAllocator(false);
        ByteBufAllocatorWithOomHandler allocator = ByteBufAllocatorBuilder.create()
                .poolingPolicy(PoolingPolicy.PooledDirect)
                .poolingConcurrency(1)
                .pooledAllocator(pooledAllocator)
                .unpooledAllocator(unpooledAllocator)
                .build();

        ByteBuf buffer = allocator.heapBuffer(8, 32);
        try {
            assertThat(buffer.isDirect()).isFalse();
            assertThat(buffer.capacity()).isGreaterThanOrEqualTo(8);
            assertThat(buffer.maxCapacity()).isEqualTo(32);
            assertThat(pooledAllocator.heapAllocations()).isEqualTo(1);
            assertThat(pooledAllocator.directAllocations()).isZero();
            assertThat(unpooledAllocator.heapAllocations()).isZero();
            assertThat(unpooledAllocator.directAllocations()).isZero();

            buffer.writeLong(123L);
            assertThat(buffer.readLong()).isEqualTo(123L);
        } finally {
            buffer.release();
        }
    }

    @Test
    void builderUsesSuppliedAllocatorsForSelectedPoolingPolicy() {
        CountingAllocator pooledAllocator = new CountingAllocator(true);
        CountingAllocator unpooledAllocator = new CountingAllocator(false);

        ByteBufAllocatorWithOomHandler pooled = ByteBufAllocatorBuilder.create()
                .poolingPolicy(PoolingPolicy.PooledDirect)
                .poolingConcurrency(1)
                .pooledAllocator(pooledAllocator)
                .unpooledAllocator(unpooledAllocator)
                .build();
        ByteBuf pooledBuffer = pooled.buffer(4, 16);
        try {
            assertThat(pooledBuffer.isDirect()).isTrue();
            assertThat(pooledAllocator.directAllocations()).isEqualTo(1);
            assertThat(unpooledAllocator.heapAllocations()).isZero();
        } finally {
            pooledBuffer.release();
        }

        ByteBufAllocatorWithOomHandler unpooled = ByteBufAllocatorBuilder.create()
                .poolingPolicy(PoolingPolicy.UnpooledHeap)
                .pooledAllocator(pooledAllocator)
                .unpooledAllocator(unpooledAllocator)
                .build();
        ByteBuf unpooledBuffer = unpooled.buffer(4, 16);
        try {
            assertThat(unpooledBuffer.isDirect()).isFalse();
            assertThat(unpooledAllocator.heapAllocations()).isEqualTo(1);
            assertThat(pooledAllocator.directAllocations()).isEqualTo(1);
        } finally {
            unpooledBuffer.release();
        }
    }

    @Test
    void pooledDirectAllocatorFallsBackToHeapWhenConfiguredForFallback() {
        OutOfMemoryError directFailure = new OutOfMemoryError("direct allocation failed");
        FailingAllocator failingPooledAllocator = new FailingAllocator(true, directFailure);
        List<OutOfMemoryError> observedErrors = new ArrayList<>();
        ByteBufAllocatorWithOomHandler allocator = ByteBufAllocatorBuilder.create()
                .poolingPolicy(PoolingPolicy.PooledDirect)
                .pooledAllocator(failingPooledAllocator)
                .unpooledAllocator(UnpooledByteBufAllocator.DEFAULT)
                .outOfMemoryPolicy(OutOfMemoryPolicy.FallbackToHeap)
                .outOfMemoryListener(observedErrors::add)
                .build();

        ByteBuf buffer = allocator.buffer(8, 32);
        try {
            assertThat(buffer.isDirect()).isFalse();
            assertThat(failingPooledAllocator.directAllocationAttempts()).isEqualTo(1);
            assertThat(observedErrors).isEmpty();
        } finally {
            buffer.release();
        }
    }

    @Test
    void directBufferDoesNotFallBackToHeapWhenFallbackPolicyIsConfigured() {
        OutOfMemoryError directFailure = new OutOfMemoryError("direct allocation failed");
        FailingAllocator failingPooledAllocator = new FailingAllocator(true, directFailure);
        CountingAllocator unpooledAllocator = new CountingAllocator(false);
        AtomicReference<OutOfMemoryError> observedError = new AtomicReference<>();
        ByteBufAllocatorWithOomHandler allocator = ByteBufAllocatorBuilder.create()
                .poolingPolicy(PoolingPolicy.PooledDirect)
                .pooledAllocator(failingPooledAllocator)
                .unpooledAllocator(unpooledAllocator)
                .outOfMemoryPolicy(OutOfMemoryPolicy.FallbackToHeap)
                .outOfMemoryListener(observedError::set)
                .build();

        assertThatThrownBy(() -> allocator.directBuffer(8, 32)).isSameAs(directFailure);
        assertThat(observedError.get()).isSameAs(directFailure);
        assertThat(failingPooledAllocator.directAllocationAttempts()).isEqualTo(1);
        assertThat(unpooledAllocator.heapAllocations()).isZero();
        assertThat(unpooledAllocator.directAllocations()).isZero();
    }

    @Test
    void throwExceptionPolicyNotifiesListenerAndRethrowsDirectAllocationFailure() {
        OutOfMemoryError directFailure = new OutOfMemoryError("direct allocation failed");
        FailingAllocator failingPooledAllocator = new FailingAllocator(true, directFailure);
        AtomicReference<OutOfMemoryError> observedError = new AtomicReference<>();
        ByteBufAllocatorWithOomHandler allocator = ByteBufAllocatorBuilder.create()
                .poolingPolicy(PoolingPolicy.PooledDirect)
                .pooledAllocator(failingPooledAllocator)
                .unpooledAllocator(UnpooledByteBufAllocator.DEFAULT)
                .outOfMemoryPolicy(OutOfMemoryPolicy.ThrowException)
                .outOfMemoryListener(observedError::set)
                .build();

        assertThatThrownBy(() -> allocator.buffer(8, 32)).isSameAs(directFailure);
        assertThat(observedError.get()).isSameAs(directFailure);
        assertThat(failingPooledAllocator.directAllocationAttempts()).isEqualTo(1);
    }

    @Test
    void setOomHandlerReplacesTheListenerUsedForLaterAllocationFailures() {
        OutOfMemoryError heapFailure = new OutOfMemoryError("heap allocation failed");
        FailingAllocator failingUnpooledAllocator = new FailingAllocator(false, heapFailure);
        AtomicInteger originalListenerCalls = new AtomicInteger();
        AtomicReference<OutOfMemoryError> replacementListenerError = new AtomicReference<>();
        ByteBufAllocatorWithOomHandler allocator = ByteBufAllocatorBuilder.create()
                .poolingPolicy(PoolingPolicy.UnpooledHeap)
                .unpooledAllocator(failingUnpooledAllocator)
                .outOfMemoryListener(error -> originalListenerCalls.incrementAndGet())
                .build();

        allocator.setOomHandler(replacementListenerError::set);

        assertThatThrownBy(() -> allocator.heapBuffer(8, 32)).isSameAs(heapFailure);
        assertThat(originalListenerCalls).hasValue(0);
        assertThat(replacementListenerError.get()).isSameAs(heapFailure);
        assertThat(failingUnpooledAllocator.heapAllocationAttempts()).isEqualTo(1);
    }

    private static void buildAllocatorWithLeakPolicy(LeakDetectionPolicy policy) {
        ByteBuf buffer = ByteBufAllocatorBuilder.create()
                .poolingPolicy(PoolingPolicy.UnpooledHeap)
                .leakDetectionPolicy(policy)
                .build()
                .buffer(1, 1);
        buffer.release();
    }

    private static final class CountingAllocator extends AbstractByteBufAllocator {
        private final ByteBufAllocator delegate;
        private final AtomicInteger heapAllocations = new AtomicInteger();
        private final AtomicInteger directAllocations = new AtomicInteger();
        private final boolean directBufferPooled;

        private CountingAllocator(boolean directBufferPooled) {
            super(directBufferPooled);
            this.directBufferPooled = directBufferPooled;
            this.delegate = UnpooledByteBufAllocator.DEFAULT;
        }

        @Override
        protected ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity) {
            heapAllocations.incrementAndGet();
            return delegate.heapBuffer(initialCapacity, maxCapacity);
        }

        @Override
        protected ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity) {
            directAllocations.incrementAndGet();
            return delegate.directBuffer(initialCapacity, maxCapacity);
        }

        @Override
        public boolean isDirectBufferPooled() {
            return directBufferPooled;
        }

        private int heapAllocations() {
            return heapAllocations.get();
        }

        private int directAllocations() {
            return directAllocations.get();
        }
    }

    private static final class FailingAllocator extends AbstractByteBufAllocator {
        private final boolean directBufferPooled;
        private final OutOfMemoryError failure;
        private final AtomicInteger heapAllocationAttempts = new AtomicInteger();
        private final AtomicInteger directAllocationAttempts = new AtomicInteger();

        private FailingAllocator(boolean directBufferPooled, OutOfMemoryError failure) {
            super(directBufferPooled);
            this.directBufferPooled = directBufferPooled;
            this.failure = failure;
        }

        @Override
        protected ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity) {
            heapAllocationAttempts.incrementAndGet();
            throw failure;
        }

        @Override
        protected ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity) {
            directAllocationAttempts.incrementAndGet();
            throw failure;
        }

        @Override
        public boolean isDirectBufferPooled() {
            return directBufferPooled;
        }

        private int heapAllocationAttempts() {
            return heapAllocationAttempts.get();
        }

        private int directAllocationAttempts() {
            return directAllocationAttempts.get();
        }
    }
}
