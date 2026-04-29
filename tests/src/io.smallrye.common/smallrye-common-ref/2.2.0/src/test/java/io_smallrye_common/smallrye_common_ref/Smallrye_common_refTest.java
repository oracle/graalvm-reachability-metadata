/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_common.smallrye_common_ref;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.smallrye.common.ref.CleanerReference;
import io.smallrye.common.ref.PhantomReference;
import io.smallrye.common.ref.Reaper;
import io.smallrye.common.ref.Reference;
import io.smallrye.common.ref.References;
import io.smallrye.common.ref.SoftReference;
import io.smallrye.common.ref.StrongReference;
import io.smallrye.common.ref.WeakReference;
import java.lang.ref.ReferenceQueue;
import java.util.EnumSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class Smallrye_common_refTest {
    @Test
    void strongReferenceExposesReferentAttachmentTypeAndClear() {
        Payload payload = new Payload("strong");
        StrongReference<Payload, String> reference = new StrongReference<>(payload, "strong-attachment");

        assertThat(reference.get()).isSameAs(payload);
        assertThat(reference.getAttachment()).isEqualTo("strong-attachment");
        assertThat(reference.getType()).isEqualTo(Reference.Type.STRONG);
        assertThat(reference.toString()).contains("strong");

        reference.clear();

        assertThat(reference.get()).isNull();
        assertThat(reference.getAttachment()).isEqualTo("strong-attachment");
    }

    @Test
    void singleArgumentConstructorsDefaultToNullAttachments() {
        Payload strongPayload = new Payload("single-strong");
        StrongReference<Payload, Object> strong = new StrongReference<>(strongPayload);
        assertThat(strong.get()).isSameAs(strongPayload);
        assertThat(strong.getAttachment()).isNull();
        assertThat(strong.getType()).isEqualTo(Reference.Type.STRONG);

        Payload weakPayload = new Payload("single-weak");
        WeakReference<Payload, Object> weak = new WeakReference<>(weakPayload);
        assertThat(weak.get()).isSameAs(weakPayload);
        assertThat(weak.getAttachment()).isNull();
        assertThat(weak.getType()).isEqualTo(Reference.Type.WEAK);

        Payload softPayload = new Payload("single-soft");
        SoftReference<Payload, Object> soft = new SoftReference<>(softPayload);
        assertThat(soft.get()).isSameAs(softPayload);
        assertThat(soft.getAttachment()).isNull();
        assertThat(soft.getType()).isEqualTo(Reference.Type.SOFT);
    }

    @Test
    void weakReferenceSupportsAttachmentQueueingAndClear() throws Exception {
        ReferenceQueue<Payload> queue = new ReferenceQueue<>();
        Payload payload = new Payload("weak");
        WeakReference<Payload, String> reference = new WeakReference<>(payload, "weak-attachment", queue);

        assertThat(reference.get()).isSameAs(payload);
        assertThat(reference.getAttachment()).isEqualTo("weak-attachment");
        assertThat(reference.getType()).isEqualTo(Reference.Type.WEAK);
        assertThat(reference.getReaper()).isNull();
        assertThat(reference.toString()).contains("weak");

        assertThat(reference.enqueue()).isTrue();
        java.lang.ref.Reference<? extends Payload> queuedReference = queue.remove(1_000L);
        assertThat(queuedReference).isSameAs(reference);

        reference.clear();
        assertThat(reference.get()).isNull();
        assertThat(reference.getAttachment()).isEqualTo("weak-attachment");
    }

    @Test
    void softReferenceSupportsAttachmentQueueingAndClear() throws Exception {
        ReferenceQueue<Payload> queue = new ReferenceQueue<>();
        Payload payload = new Payload("soft");
        SoftReference<Payload, String> reference = new SoftReference<>(payload, "soft-attachment", queue);

        assertThat(reference.get()).isSameAs(payload);
        assertThat(reference.getAttachment()).isEqualTo("soft-attachment");
        assertThat(reference.getType()).isEqualTo(Reference.Type.SOFT);
        assertThat(reference.getReaper()).isNull();
        assertThat(reference.toString()).contains("soft");

        assertThat(reference.enqueue()).isTrue();
        java.lang.ref.Reference<? extends Payload> queuedReference = queue.remove(1_000L);
        assertThat(queuedReference).isSameAs(reference);

        reference.clear();
        assertThat(reference.get()).isNull();
        assertThat(reference.getAttachment()).isEqualTo("soft-attachment");
    }

    @Test
    void phantomReferenceKeepsOnlyAttachmentAndCanBeQueued() throws Exception {
        ReferenceQueue<Payload> queue = new ReferenceQueue<>();
        Payload payload = new Payload("phantom");
        PhantomReference<Payload, String> reference = new PhantomReference<>(payload, "phantom-attachment", queue);

        assertThat(reference.get()).isNull();
        assertThat(reference.getAttachment()).isEqualTo("phantom-attachment");
        assertThat(reference.getType()).isEqualTo(Reference.Type.PHANTOM);
        assertThat(reference.getReaper()).isNull();
        assertThat(reference.toString()).isEqualTo("phantom reference");

        assertThat(reference.enqueue()).isTrue();
        java.lang.ref.Reference<? extends Payload> queuedReference = queue.remove(1_000L);
        assertThat(queuedReference).isSameAs(reference);
    }

    @Test
    void factoryCreatesExpectedReferenceImplementationForEachConcreteType() throws Exception {
        Payload strongPayload = new Payload("factory-strong");
        Reference<Payload, String> strong = References.create(
                Reference.Type.STRONG,
                strongPayload,
                "strong-attachment");
        assertReference(strong, StrongReference.class, Reference.Type.STRONG, strongPayload, "strong-attachment");

        Payload weakPayload = new Payload("factory-weak");
        Reference<Payload, String> weak = References.create(Reference.Type.WEAK, weakPayload, "weak-attachment");
        assertReference(weak, WeakReference.class, Reference.Type.WEAK, weakPayload, "weak-attachment");

        Payload softPayload = new Payload("factory-soft");
        Reference<Payload, String> soft = References.create(Reference.Type.SOFT, softPayload, "soft-attachment");
        assertReference(soft, SoftReference.class, Reference.Type.SOFT, softPayload, "soft-attachment");

        ReferenceQueue<Payload> queue = new ReferenceQueue<>();
        Payload phantomPayload = new Payload("factory-phantom");
        Reference<Payload, String> phantom = References.create(
                Reference.Type.PHANTOM,
                phantomPayload,
                "phantom-attachment",
                queue);
        assertReference(phantom, PhantomReference.class, Reference.Type.PHANTOM, null, "phantom-attachment");
        assertThat(((java.lang.ref.Reference<?>) phantom).enqueue()).isTrue();
        assertThat(queue.remove(1_000L)).isSameAs(phantom);
    }

    @Test
    void factoryWithoutQueueHandlesPhantomAsNullReferenceShapeAndNullQueueDelegates() {
        Reference<Payload, String> phantomWithAttachment = References.create(
                Reference.Type.PHANTOM,
                new Payload("phantom-without-queue"),
                "phantom-attachment");
        assertThat(phantomWithAttachment).isInstanceOf(StrongReference.class);
        assertThat(phantomWithAttachment.get()).isNull();
        assertThat(phantomWithAttachment.getAttachment()).isEqualTo("phantom-attachment");
        assertThat(phantomWithAttachment.getType()).isEqualTo(Reference.Type.STRONG);

        Reference<Payload, String> phantomWithoutAttachment = References.create(
                Reference.Type.PHANTOM,
                new Payload("phantom-without-attachment"),
                null);
        assertThat(phantomWithoutAttachment).isSameAs(References.getNullReference());

        Payload weakPayload = new Payload("null-queue-weak");
        Reference<Payload, String> weak = References.create(
                Reference.Type.WEAK,
                weakPayload,
                "weak-attachment",
                (ReferenceQueue<Payload>) null);
        assertReference(weak, WeakReference.class, Reference.Type.WEAK, weakPayload, "weak-attachment");
    }

    @Test
    void factoryHandlesNullReferentsAndNullReferenceRequestsConsistently() {
        Reference<Payload, String> nullSingleton = References.getNullReference();
        assertThat(nullSingleton.get()).isNull();
        assertThat(nullSingleton.getAttachment()).isNull();
        assertThat(nullSingleton.getType()).isEqualTo(Reference.Type.STRONG);
        assertThat(References.<Payload, String>create(Reference.Type.WEAK, null, null)).isSameAs(nullSingleton);
        assertThat(References.<Payload, String>create(Reference.Type.NULL, null, null)).isSameAs(nullSingleton);

        Reference<Payload, String> attachedNull = References.create(
                Reference.Type.NULL,
                new Payload("ignored"),
                "null-attachment");
        assertThat(attachedNull).isInstanceOf(StrongReference.class);
        assertThat(attachedNull.get()).isNull();
        assertThat(attachedNull.getAttachment()).isEqualTo("null-attachment");
        assertThat(attachedNull.getType()).isEqualTo(Reference.Type.STRONG);

        assertThatThrownBy(() -> References.create(null, new Payload("invalid"), "attachment"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type");
    }

    @Test
    void factoryUsesProvidedReferenceQueueForWeakSoftAndPhantomReferences() throws Exception {
        assertFactoryQueuesReference(Reference.Type.WEAK, new Payload("queued-weak"), "weak-queue");
        assertFactoryQueuesReference(Reference.Type.SOFT, new Payload("queued-soft"), "soft-queue");
        assertFactoryQueuesReference(Reference.Type.PHANTOM, new Payload("queued-phantom"), "phantom-queue");
    }

    @Test
    void reaperConstructorsExposeReaperAndProcessEnqueuedReferences() throws Exception {
        assertReaperProcessesReference(Reference.Type.WEAK, new Payload("reaped-weak"), "weak-reaper");
        assertReaperProcessesReference(Reference.Type.SOFT, new Payload("reaped-soft"), "soft-reaper");
        assertReaperProcessesReference(Reference.Type.PHANTOM, new Payload("reaped-phantom"), "phantom-reaper");
    }

    @Test
    void directReaperConstructorsUseProvidedReaperForWeakSoftAndPhantomReferences() throws Exception {
        CountDownLatch latch = new CountDownLatch(3);
        ConcurrentLinkedQueue<String> reapedAttachments = new ConcurrentLinkedQueue<>();
        Reaper<Payload, String> reaper = reference -> {
            reapedAttachments.add(reference.getAttachment());
            latch.countDown();
        };

        WeakReference<Payload, String> weak = new WeakReference<>(
                new Payload("direct-reaped-weak"),
                "direct-weak-reaper",
                reaper);
        SoftReference<Payload, String> soft = new SoftReference<>(
                new Payload("direct-reaped-soft"),
                "direct-soft-reaper",
                reaper);
        PhantomReference<Payload, String> phantom = new PhantomReference<>(
                new Payload("direct-reaped-phantom"),
                "direct-phantom-reaper",
                reaper);

        assertThat(weak.getReaper()).isSameAs(reaper);
        assertThat(soft.getReaper()).isSameAs(reaper);
        assertThat(phantom.getReaper()).isSameAs(reaper);

        assertThat(weak.enqueue()).isTrue();
        assertThat(soft.enqueue()).isTrue();
        assertThat(phantom.enqueue()).isTrue();
        assertThat(latch.await(5L, TimeUnit.SECONDS)).isTrue();
        assertThat(reapedAttachments).containsExactlyInAnyOrder(
                "direct-weak-reaper",
                "direct-soft-reaper",
                "direct-phantom-reaper");
    }

    @Test
    void sharedReaperThreadContinuesAfterAReaperThrows() throws Exception {
        CountDownLatch throwingReaperLatch = new CountDownLatch(1);
        Reaper<Payload, String> throwingReaper = reference -> {
            throwingReaperLatch.countDown();
            throw new IllegalStateException("reaper failure");
        };
        WeakReference<Payload, String> throwingReference = new WeakReference<>(
                new Payload("throwing-reaper"),
                "throwing-reaper-attachment",
                throwingReaper);

        assertThat(throwingReference.enqueue()).isTrue();
        assertThat(throwingReaperLatch.await(5L, TimeUnit.SECONDS)).isTrue();

        CountDownLatch followingReaperLatch = new CountDownLatch(1);
        AtomicReference<String> followingAttachment = new AtomicReference<>();
        Reaper<Payload, String> followingReaper = reference -> {
            followingAttachment.set(reference.getAttachment());
            followingReaperLatch.countDown();
        };
        WeakReference<Payload, String> followingReference = new WeakReference<>(
                new Payload("following-reaper"),
                "following-reaper-attachment",
                followingReaper);

        assertThat(followingReference.enqueue()).isTrue();
        assertThat(followingReaperLatch.await(5L, TimeUnit.SECONDS)).isTrue();
        assertThat(followingAttachment.get()).isEqualTo("following-reaper-attachment");
    }

    @Test
    void cleanerReferenceIsIdentityBasedAndUsesTheSharedReaperQueue() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Reference<Payload, String>> reapedReference = new AtomicReference<>();
        Reaper<Payload, String> reaper = reference -> {
            reapedReference.set(reference);
            latch.countDown();
        };
        Payload payload = new Payload("cleaner");
        CleanerReference<Payload, String> reference = new CleanerReference<>(payload, "cleaner-attachment", reaper);

        assertThat(reference.get()).isNull();
        assertThat(reference.getAttachment()).isEqualTo("cleaner-attachment");
        assertThat(reference.getType()).isEqualTo(Reference.Type.PHANTOM);
        assertThat(reference.getReaper()).isSameAs(reaper);
        assertThat(reference).isEqualTo(reference);
        assertThat(reference).isNotEqualTo(new Object());
        assertThat(reference.hashCode()).isEqualTo(System.identityHashCode(reference));

        assertThat(reference.enqueue()).isTrue();
        assertThat(latch.await(5L, TimeUnit.SECONDS)).isTrue();
        assertThat(reapedReference.get()).isSameAs(reference);
    }

    @Test
    void referenceTypeMembershipHelpersCoverFixedAndVariableArityChecks() {
        assertThat(Reference.Type.isFull(EnumSet.allOf(Reference.Type.class))).isTrue();
        assertThat(Reference.Type.isFull(EnumSet.of(Reference.Type.STRONG, Reference.Type.WEAK))).isFalse();
        assertThat(Reference.Type.isFull(null)).isFalse();

        assertThat(Reference.Type.WEAK.in(Reference.Type.WEAK)).isTrue();
        assertThat(Reference.Type.WEAK.in(Reference.Type.STRONG)).isFalse();
        assertThat(Reference.Type.SOFT.in(Reference.Type.STRONG, Reference.Type.SOFT)).isTrue();
        assertThat(Reference.Type.SOFT.in(Reference.Type.STRONG, Reference.Type.WEAK)).isFalse();
        assertThat(Reference.Type.PHANTOM.in(
                Reference.Type.STRONG,
                Reference.Type.WEAK,
                Reference.Type.PHANTOM)).isTrue();
        assertThat(Reference.Type.PHANTOM.in(
                Reference.Type.STRONG,
                Reference.Type.WEAK,
                Reference.Type.SOFT)).isFalse();
        assertThat(Reference.Type.NULL.in(
                Reference.Type.STRONG,
                Reference.Type.WEAK,
                Reference.Type.SOFT,
                Reference.Type.NULL)).isTrue();
        assertThat(Reference.Type.NULL.in(Reference.Type.STRONG, Reference.Type.WEAK, Reference.Type.SOFT)).isFalse();
        assertThat(Reference.Type.NULL.in((Reference.Type[]) null)).isFalse();
    }

    private static void assertReference(
            Reference<Payload, String> reference,
            Class<?> implementationType,
            Reference.Type type,
            Payload expectedReferent,
            String expectedAttachment) {
        assertThat(reference).isInstanceOf(implementationType);
        assertThat(reference.getType()).isEqualTo(type);
        assertThat(reference.get()).isSameAs(expectedReferent);
        assertThat(reference.getAttachment()).isEqualTo(expectedAttachment);
    }

    private static void assertFactoryQueuesReference(
            Reference.Type type,
            Payload payload,
            String attachment) throws Exception {
        ReferenceQueue<Payload> queue = new ReferenceQueue<>();
        Reference<Payload, String> reference = References.create(type, payload, attachment, queue);

        assertThat(reference.getType()).isEqualTo(type);
        assertThat(reference.getAttachment()).isEqualTo(attachment);
        assertThat(((java.lang.ref.Reference<?>) reference).enqueue()).isTrue();
        assertThat(queue.remove(1_000L)).isSameAs(reference);
    }

    private static void assertReaperProcessesReference(
            Reference.Type type,
            Payload payload,
            String attachment) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Reference<Payload, String>> reapedReference = new AtomicReference<>();
        Reaper<Payload, String> reaper = reference -> {
            reapedReference.set(reference);
            latch.countDown();
        };
        Reference<Payload, String> reference = References.create(type, payload, attachment, reaper);

        assertThat(reference.getType()).isEqualTo(type);
        assertThat(reference.getAttachment()).isEqualTo(attachment);
        assertThat(reference).isInstanceOf(java.lang.ref.Reference.class);
        assertThat(((java.lang.ref.Reference<?>) reference).enqueue()).isTrue();
        assertThat(latch.await(5L, TimeUnit.SECONDS)).isTrue();
        assertThat(reapedReference.get()).isSameAs(reference);
    }

    private static final class Payload {
        private final String name;

        private Payload(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
