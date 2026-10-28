/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_collections.commons_collections;

import org.apache.commons.collections.CursorableLinkedList;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

public class CursorableLinkedListTest {
    @Test
    void allocatesTypedArrayWhenDestinationIsTooSmall() {
        CursorableLinkedList list = new CursorableLinkedList();
        list.add("queued");
        list.add(null);
        list.add("ready");
        String[] destination = new String[1];

        Object[] result = list.toArray(destination);

        assertNotSame(destination, result);
        assertEquals(String[].class, result.getClass());
        assertArrayEquals(new String[]{"queued", null, "ready"}, result);
        assertArrayEquals(new String[]{null}, destination);
    }

    @Test
    void reusesLargerTypedArrayAndTerminatesElementsWithNull() {
        CursorableLinkedList list = new CursorableLinkedList();
        list.add("queued");
        list.add("ready");
        String[] destination = {"old", "old", "old", "untouched"};

        Object[] result = list.toArray(destination);

        assertSame(destination, result);
        assertArrayEquals(new String[]{"queued", "ready", null, "untouched"}, result);
    }

    @Test
    void serializationRestoresMutableEntriesAndSharedReferences() throws IOException, ClassNotFoundException {
        Date firstDeadline = new Date(10000L);
        Date secondDeadline = new Date(20000L);
        CursorableLinkedList original = new CursorableLinkedList();
        original.add(firstDeadline);
        original.add(secondDeadline);
        original.add(firstDeadline);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
        }

        CursorableLinkedList restored;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (CursorableLinkedList) input.readObject();
        }

        assertEquals(Arrays.asList(firstDeadline, secondDeadline, firstDeadline), restored);
        Date restoredDeadline = (Date) restored.getFirst();
        assertNotSame(firstDeadline, restoredDeadline);
        assertNotSame(secondDeadline, restored.get(1));
        assertSame(restoredDeadline, restored.getLast());

        restoredDeadline.setTime(30000L);
        assertEquals(new Date(30000L), restored.getFirst());
        assertEquals(new Date(30000L), restored.getLast());
        assertEquals(new Date(20000L), restored.get(1));
        assertEquals(10000L, firstDeadline.getTime());
        assertSame(firstDeadline, original.getFirst());
        assertSame(firstDeadline, original.getLast());
    }

    @Test
    void serializationPreservesOrderNullsAndIndependentListOperations() throws IOException, ClassNotFoundException {
        CursorableLinkedList original = new CursorableLinkedList();
        original.add("queued");
        original.add(null);
        original.add("ready");
        original.add("queued");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
        }

        CursorableLinkedList restored;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (CursorableLinkedList) input.readObject();
        }

        List<String> expected = Arrays.asList("queued", null, "ready", "queued");
        assertNotSame(original, restored);
        assertEquals(expected, restored);
        assertEquals(4, restored.size());
        assertEquals("queued", restored.removeFirst());
        assertEquals("queued", restored.removeLast());
        restored.addFirst("started");
        restored.addLast("finished");
        assertEquals(Arrays.asList("started", null, "ready", "finished"), restored);
        assertEquals(expected, original);
    }
}
