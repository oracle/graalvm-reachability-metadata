/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_modules.jboss_modules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.ref.ReferenceQueue;

import org.jboss.modules.ref.Reference;
import org.jboss.modules.ref.Reference.Type;
import org.jboss.modules.ref.References;
import org.junit.jupiter.api.Test;

public class JbossModulesReferencesTest {
    @Test
    void createsStrongWeakAndSoftReferencesWithAttachments() {
        Object strongValue = new Object();
        Reference<Object, String> strongReference = References.create(Type.STRONG, strongValue, "strong-attachment");
        Object weakValue = new Object();
        Reference<Object, String> weakReference = References.create(Type.WEAK, weakValue, "weak-attachment");
        Object softValue = new Object();
        Reference<Object, String> softReference = References.create(Type.SOFT, softValue, "soft-attachment");

        assertReference(strongReference, Type.STRONG, strongValue, "strong-attachment");
        assertReference(weakReference, Type.WEAK, weakValue, "weak-attachment");
        assertReference(softReference, Type.SOFT, softValue, "soft-attachment");

        strongReference.clear();
        weakReference.clear();
        softReference.clear();

        assertNull(strongReference.get());
        assertNull(weakReference.get());
        assertNull(softReference.get());
        assertEquals("strong-attachment", strongReference.getAttachment());
        assertEquals("weak-attachment", weakReference.getAttachment());
        assertEquals("soft-attachment", softReference.getAttachment());
    }

    @Test
    void createsQueuedWeakSoftAndPhantomReferences() {
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        Object weakValue = new Object();
        Object softValue = new Object();
        Object phantomValue = new Object();

        Reference<Object, String> weakReference = References.create(Type.WEAK, weakValue, "weak", queue);
        Reference<Object, String> softReference = References.create(Type.SOFT, softValue, "soft", queue);
        Reference<Object, String> phantomReference = References.create(Type.PHANTOM, phantomValue, "phantom", queue);

        assertReference(weakReference, Type.WEAK, weakValue, "weak");
        assertReference(softReference, Type.SOFT, softValue, "soft");
        assertEquals(Type.PHANTOM, phantomReference.getType());
        assertNull(phantomReference.get());
        assertEquals("phantom", phantomReference.getAttachment());
        assertNull(queue.poll());
    }

    @Test
    void exposesSingletonNullReferenceAndRejectsUnqueuedPhantomReference() {
        Reference<Object, String> nullReference = References.getNullReference();
        Reference<Object, String> createdNullReference = References.create(Type.NULL, new Object(), "ignored");

        assertSame(nullReference, createdNullReference);
        assertEquals(Type.NULL, nullReference.getType());
        assertNull(nullReference.get());
        assertNull(nullReference.getAttachment());
        nullReference.clear();
        assertTrue(nullReference.toString().contains("NULL reference"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> References.create(Type.PHANTOM, new Object(), "missing-queue"));
        assertNotNull(exception.getMessage());
    }

    private static void assertReference(Reference<Object, String> reference, Type expectedType,
            Object expectedValue, String expectedAttachment) {
        assertEquals(expectedType, reference.getType());
        assertSame(expectedValue, reference.get());
        assertEquals(expectedAttachment, reference.getAttachment());
    }
}
