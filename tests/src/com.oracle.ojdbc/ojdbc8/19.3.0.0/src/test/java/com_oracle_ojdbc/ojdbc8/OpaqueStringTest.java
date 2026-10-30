/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.ojdbc8;

import static org.assertj.core.api.Assertions.assertThat;

import oracle.jdbc.internal.OpaqueString;
import oracle.jdbc.oracore.Util;
import org.junit.jupiter.api.Test;

public class OpaqueStringTest {
    @Test
    void preservesCredentialCharactersAcrossSerialization() throws Exception {
        String credential = "credential-\u00e9-123";
        try (OpaqueString original = OpaqueString.newOpaqueString(credential)) {
            byte[] image = Util.serializeObject(original);
            try (OpaqueString restored = (OpaqueString) Util.deserializeObject(image)) {
                assertThat(restored.isNull()).isFalse();
                assertThat(restored.get()).isEqualTo(credential);
                assertThat(restored.get()).isEqualTo(original.get());
            }
        }
    }
}
