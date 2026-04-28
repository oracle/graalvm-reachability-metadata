/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mozilla.rhino;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.ScriptableObject;

public class ScriptableObjectInnerRelinkedSlotTest {

    @Test
    void serializesRelinkedSlotCreatedByPropertyTableGrowth() throws Exception {
        final SlotExposingScriptableObject object = new SlotExposingScriptableObject();
        object.defineProperty("a", "first", ScriptableObject.EMPTY);
        object.defineProperty("e", "same initial bucket", ScriptableObject.EMPTY);
        object.defineProperty("b", "different bucket", ScriptableObject.EMPTY);
        object.defineProperty("c", "grows the property table", ScriptableObject.EMPTY);

        assertThat(object.get("a", object)).isEqualTo("first");

        final Object relinkedSlot = object.querySlot("a");
        final byte[] serializedSlot = serialize(relinkedSlot);

        assertThat(serializedSlot).isNotEmpty();
    }

    private static byte[] serialize(final Object object) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(object);
        }
        return bytes.toByteArray();
    }

    private static final class SlotExposingScriptableObject extends ScriptableObject {

        private static final int SLOT_QUERY = 1;

        @Override
        public String getClassName() {
            return "SlotExposingScriptableObject";
        }

        Object querySlot(final String name) {
            return getSlot(null, name, SLOT_QUERY);
        }
    }
}
