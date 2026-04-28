/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mozilla.rhino;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ObjArray;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.serialize.ScriptableInputStream;

public class ScriptableInputStreamTest {

    @Test
    void resolvesClassesWithCurrentContextApplicationClassLoader() throws Exception {
        final Context context = Context.enter();
        try {
            context.setApplicationClassLoader(ScriptableInputStreamTest.class.getClassLoader());
            final Scriptable scope = context.initStandardObjects();

            final ObjArray restored = deserializeWithScriptableInputStream(serialize(sampleArray()), scope);

            assertThat(restored.toArray()).containsExactly("resolved by context class loader");
        } finally {
            Context.exit();
        }
    }

    @Test
    void fallsBackToDefaultClassResolutionWithoutCurrentContext() throws Exception {
        final ObjArray restored = deserializeWithScriptableInputStream(serialize(sampleArray()), null);

        assertThat(restored.toArray()).containsExactly("resolved by context class loader");
    }

    private static ObjArray sampleArray() {
        final ObjArray array = new ObjArray();
        array.add("resolved by context class loader");
        return array;
    }

    private static byte[] serialize(final ObjArray array) throws IOException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(array);
        }
        return outputStream.toByteArray();
    }

    private static ObjArray deserializeWithScriptableInputStream(final byte[] bytes, final Scriptable scope)
            throws IOException, ClassNotFoundException {
        try (ScriptableInputStream inputStream =
                new ScriptableInputStream(new ByteArrayInputStream(bytes), scope)) {
            return (ObjArray) inputStream.readObject();
        }
    }
}
