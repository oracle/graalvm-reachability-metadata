/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_twitter.chill_java;

import static org.assertj.core.api.Assertions.assertThat;

import com.twitter.chill.Base64;
import org.junit.jupiter.api.Test;

public class Base64Test {
    @Test
    void roundTripsSerializedObject() throws Exception {
        String original = "chill-java base64 serialization payload";

        String encoded = Base64.encodeObject(original);
        Object decoded = Base64.decodeToObject(encoded);

        assertThat(decoded).isEqualTo(original);
    }
}
