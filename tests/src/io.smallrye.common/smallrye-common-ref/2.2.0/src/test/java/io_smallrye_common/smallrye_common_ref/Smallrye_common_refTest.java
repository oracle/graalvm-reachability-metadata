/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_common.smallrye_common_ref;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.ref.ReferenceQueue;
import java.util.EnumSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.smallrye.common.ref.CleanerReference;
import io.smallrye.common.ref.PhantomReference;
import io.smallrye.common.ref.Reaper;
import io.smallrye.common.ref.Reference;
import io.smallrye.common.ref.References;
import io.smallrye.common.ref.SoftReference;
import io.smallrye.common.ref.StrongReference;
import io.smallrye.common.ref.WeakReference;
import org.junit.jupiter.api.Test;

public class Smallrye_common_refTest {
    @Test
    void strongReferenceKeepsReferentUntilClearedAndRetainsAttachment() {
        Object referent = new Object();
        StrongReference<Object, String> reference = new StrongReference<>(referent, "primary");
        StrongReference<Object, Object> noAttachmentReference = new StrongReference<>(referent);

        assertThat(noAttachmentReference.get()).isSameAs(referent);
        assertThat(noAttachmentReference.getAttachment()).isNull();
        assertThat(reference.get()).isSameAs(referent);
        assertThat(reference.getAttachment()).isEqualTo("primary");
        assertThat(reference.getType()).isEqualTo(Reference.Type.STRONG);
        assertThat(reference.toString()).isEqualTo("strong reference to " + referent);

        reference.clear();

        assertThat(reference.get()).isNull();
        assertThat(reference.getAttachment()).isEqualTo("primary");
        assertThat(reference.toString()).isEqualTo("strong reference to null");
    }

    @Test
    void weakReferenceSupportsAttachmentsQueuesClearingAndTypeInspection() throws Exception {
        Object referent = new Object();
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        WeakReference<Object, String> reference = new WeakReference<>(referent, "weak-attachment", queue);
        WeakReference<Object, Object> noAttachmentReference = new WeakReference<>(referent);

        assertThat(noAttachmentReference.get()).isSameAs(referent);
        assertThat(noAttachmentReference.getAttachment()).isNull();
        assertThat(reference.get()).isSameAs(referent);
        assertThat(reference.getAttachment()).isEqualTo("weak-attachment");
        assertThat(reference.getType()).isEqualTo(Reference.Type.WEAK);
        assertThat(reference.getReaper()).isNull();
        assertThat(reference.toString()).isEqualTo("weak reference to " + referent);

        assertThat(reference.enqueue()).isTrue();
        assertQueued(queue, reference);

        reference.clear();

        assertThat(reference.get()).isNull();
        assertThat(reference.toString()).isEqualTo("weak reference to null");
    }

    @Test
    void softReferenceSupportsAttachmentsQueuesClearingAndTypeInspection() throws Exception {
        Object referent = new Object();
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        SoftReference<Object, String> reference = new SoftReference<>(referent, "soft-attachment", queue);
        SoftReference<Object, Object> noAttachmentReference = new SoftReference<>(referent);

        assertThat(noAttachmentReference.get()).isSameAs(referent);
        assertThat(noAttachmentReference.getAttachment()).isNull();
        assertThat(reference.get()).isSameAs(referent);
        assertThat(reference.getAttachment()).isEqualTo("soft-attachment");
        assertThat(reference.getType()).isEqualTo(Reference.Type.SOFT);
        assertThat(reference.getReaper()).isNull();
        assertThat(reference.toString()).isEqualTo("soft reference to " + referent);

        assertThat(reference.enqueue()).isTrue();
        assertQueued(queue, reference);

        reference.clear();

        assertThat(reference.get()).isNull();
        assertThat(reference.toString()).isEqualTo("soft reference to null");
    }

    @Test
    void phantomReferenceAlwaysHidesReferentAndCanBeQueued() throws Exception {
        Object referent = new Object();
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        PhantomReference<Object, String> reference = new PhantomReference<>(referent, "phantom-attachment", queue);

        assertThat(reference.get()).isNull();
        assertThat(reference.getAttachment()).isEqualTo("phantom-attachment");
        assertThat(reference.getType()).isEqualTo(Reference.Type.PHANTOM);
        assertThat(reference.getReaper()).isNull();
        assertThat(reference.toString()).isEqualTo("phantom reference");

        assertThat(reference.enqueue()).isTrue();
        assertQueued(queue, reference);

        reference.clear();

        assertThat(reference.get()).isNull();
        assertThat(reference.getAttachment()).isEqualTo("phantom-attachment");
    }

    @Test
    void reaperBackedReferencesInvokeTheirReaperWhenEnqueued() throws Exception {
        ConcurrentLinkedQueue<String> reapedAttachments = new ConcurrentLinkedQueue<>();
        CountDownLatch reaped = new CountDownLatch(3);
        Reaper<Object, String> reaper = reference -> {
            reapedAttachments.add(reference.getAttachment());
            reaped.countDown();
        };
        Object weakReferent = new Object();
        Object softReferent = new Object();
        Object phantomReferent = new Object();
        WeakReference<Object, String> weakReference = new WeakReference<>(weakReferent, "weak-reaper", reaper);
        SoftReference<Object, String> softReference = new SoftReference<>(softReferent, "soft-reaper", reaper);
        PhantomReference<Object, String> phantomReference = new PhantomReference<>(
                phantomReferent,
                "phantom-reaper",
                reaper);

        assertThat(weakReference.getReaper()).isSameAs(reaper);
        assertThat(softReference.getReaper()).isSameAs(reaper);
        assertThat(phantomReference.getReaper()).isSameAs(reaper);
        assertThat(weakReference.enqueue()).isTrue();
        assertThat(softReference.enqueue()).isTrue();
        assertThat(phantomReference.enqueue()).isTrue();

        assertThat(reaped.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(reapedAttachments).containsExactlyInAnyOrder("weak-reaper", "soft-reaper", "phantom-reaper");
    }

    @Test
    void cleanerReferenceIsRetainedUntilQueuedAndThenReaped() throws Exception {
        AtomicReference<Reference<Object, String>> reapedReference = new AtomicReference<>();
        CountDownLatch reaped = new CountDownLatch(1);
        Reaper<Object, String> reaper = reference -> {
            reapedReference.set(reference);
            reaped.countDown();
        };
        Object referent = new Object();
        CleanerReference<Object, String> reference = new CleanerReference<>(referent, "cleaner-attachment", reaper);

        assertThat(reference.get()).isNull();
        assertThat(reference.getType()).isEqualTo(Reference.Type.PHANTOM);
        assertThat(reference.getAttachment()).isEqualTo("cleaner-attachment");
        assertThat(reference.getReaper()).isSameAs(reaper);
        assertThat(reference.equals(reference)).isTrue();
        assertThat(reference.equals(new Object())).isFalse();
        assertThat(reference.hashCode()).isEqualTo(reference.hashCode());

        assertThat(reference.enqueue()).isTrue();

        assertThat(reaped.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(reapedReference.get()).isSameAs(reference);
    }

    @Test
    void referencesFactoryCreatesStrongWeakSoftAndPhantomReferences() throws Exception {
        Object strongReferent = new Object();
        Reference<Object, String> strong = References.create(Reference.Type.STRONG, strongReferent, "strong");
        assertThat(strong).isInstanceOf(StrongReference.class);
        assertThat(strong.get()).isSameAs(strongReferent);
        assertThat(strong.getAttachment()).isEqualTo("strong");
        assertThat(strong.getType()).isEqualTo(Reference.Type.STRONG);

        Object weakReferent = new Object();
        ReferenceQueue<Object> weakQueue = new ReferenceQueue<>();
        Reference<Object, String> weak = References.create(Reference.Type.WEAK, weakReferent, "weak", weakQueue);
        assertThat(weak).isInstanceOf(WeakReference.class);
        assertThat(weak.get()).isSameAs(weakReferent);
        assertThat(weak.getAttachment()).isEqualTo("weak");
        assertThat(weak.getType()).isEqualTo(Reference.Type.WEAK);
        assertThat(((WeakReference<Object, String>) weak).enqueue()).isTrue();
        assertQueued(weakQueue, (java.lang.ref.Reference<?>) weak);

        Object softReferent = new Object();
        ReferenceQueue<Object> softQueue = new ReferenceQueue<>();
        Reference<Object, String> soft = References.create(Reference.Type.SOFT, softReferent, "soft", softQueue);
        assertThat(soft).isInstanceOf(SoftReference.class);
        assertThat(soft.get()).isSameAs(softReferent);
        assertThat(soft.getAttachment()).isEqualTo("soft");
        assertThat(soft.getType()).isEqualTo(Reference.Type.SOFT);
        assertThat(((SoftReference<Object, String>) soft).enqueue()).isTrue();
        assertQueued(softQueue, (java.lang.ref.Reference<?>) soft);

        Object phantomReferent = new Object();
        ReferenceQueue<Object> phantomQueue = new ReferenceQueue<>();
        Reference<Object, String> phantom = References.create(
                Reference.Type.PHANTOM,
                phantomReferent,
                "phantom",
                phantomQueue);
        assertThat(phantom).isInstanceOf(PhantomReference.class);
        assertThat(phantom.get()).isNull();
        assertThat(phantom.getAttachment()).isEqualTo("phantom");
        assertThat(phantom.getType()).isEqualTo(Reference.Type.PHANTOM);
        assertThat(((PhantomReference<Object, String>) phantom).enqueue()).isTrue();
        assertQueued(phantomQueue, (java.lang.ref.Reference<?>) phantom);
    }

    @Test
    void referencesFactoryHandlesReapersNullInputsAndNullReferenceSingleton() {
        AtomicReference<Reference<Object, String>> ignoredStrongReaper = new AtomicReference<>();
        Reaper<Object, String> reaper = ignoredStrongReaper::set;
        Object weakReferent = new Object();
        Reference<Object, String> weak = References.create(Reference.Type.WEAK, weakReferent, "weak", reaper);

        assertThat(weak).isInstanceOf(WeakReference.class);
        assertThat(((WeakReference<Object, String>) weak).getReaper()).isSameAs(reaper);

        Object phantomReferent = new Object();
        Reference<Object, String> phantomWithoutQueue = References.create(
                Reference.Type.PHANTOM,
                phantomReferent,
                "phantom-without-queue");
        assertThat(phantomWithoutQueue).isInstanceOf(StrongReference.class);
        assertThat(phantomWithoutQueue.get()).isNull();
        assertThat(phantomWithoutQueue.getAttachment()).isEqualTo("phantom-without-queue");

        Reference<Object, String> nullReferent = References.create(Reference.Type.WEAK, null, "preserved-attachment");
        assertThat(nullReferent).isInstanceOf(StrongReference.class);
        assertThat(nullReferent.get()).isNull();
        assertThat(nullReferent.getAttachment()).isEqualTo("preserved-attachment");

        Reference<Object, String> nullReference = References.getNullReference();
        Reference<String, Integer> sameNullReference = References.getNullReference();
        assertThat(nullReference).isSameAs(sameNullReference);
        assertThat(nullReference.get()).isNull();
        assertThat(nullReference.getAttachment()).isNull();

        Reference<Object, String> createdNullReference = References.create(Reference.Type.NULL, new Object(), null);
        assertThat(createdNullReference).isSameAs(nullReference);

        assertThatThrownBy(() -> References.create(null, "value", "attachment"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void referenceTypeMembershipHelpersRecognizeFullSetsAndCandidates() {
        assertThat(Reference.Type.isFull(EnumSet.allOf(Reference.Type.class))).isTrue();
        assertThat(Reference.Type.isFull(EnumSet.of(Reference.Type.STRONG, Reference.Type.WEAK))).isFalse();
        assertThat(Reference.Type.isFull(null)).isFalse();

        assertThat(Reference.Type.WEAK.in(Reference.Type.WEAK)).isTrue();
        assertThat(Reference.Type.WEAK.in(Reference.Type.STRONG)).isFalse();
        assertThat(Reference.Type.WEAK.in(Reference.Type.STRONG, Reference.Type.WEAK)).isTrue();
        assertThat(Reference.Type.WEAK.in(Reference.Type.STRONG, Reference.Type.SOFT)).isFalse();
        assertThat(Reference.Type.WEAK.in(Reference.Type.STRONG, Reference.Type.SOFT, Reference.Type.WEAK)).isTrue();
        assertThat(Reference.Type.WEAK.in(Reference.Type.STRONG, Reference.Type.SOFT,
                Reference.Type.PHANTOM)).isFalse();
        assertThat(Reference.Type.WEAK.in(Reference.Type.STRONG, Reference.Type.SOFT, Reference.Type.WEAK,
                Reference.Type.NULL)).isTrue();
        assertThat(Reference.Type.WEAK.in((Reference.Type[]) null)).isFalse();
    }

    private static void assertQueued(ReferenceQueue<Object> queue, java.lang.ref.Reference<?> expected)
            throws InterruptedException {
        java.lang.ref.Reference<?> queued = queue.remove(1_000);
        assertThat(queued).isSameAs(expected);
        assertThat(queue.poll()).isNull();
    }
}
