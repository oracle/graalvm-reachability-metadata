/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_jamesmurty_utils.java_xmlbuilder;

import net.iharder.base64.Base64;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Base64Test {
    @Test
    void encodedSerializableObjectCanBeDecoded() {
        String original = "XML builder Base64 serialization payload";

        String encodedObject = Base64.encodeObject(original, Base64.DONT_BREAK_LINES);
        Object decodedObject = Base64.decodeToObject(encodedObject);

        assertThat(encodedObject).isNotBlank();
        assertThat(decodedObject).isEqualTo(original);
    }

    @Test
    void gzipEncodedSerializableObjectCanBeDecoded() {
        String original = "Compressible Base64 serialization payload for XML builder";

        String encodedObject = Base64.encodeObject(original, Base64.GZIP | Base64.DONT_BREAK_LINES);
        Object decodedObject = Base64.decodeToObject(encodedObject);

        assertThat(encodedObject).isNotBlank();
        assertThat(decodedObject).isEqualTo(original);
    }
}
