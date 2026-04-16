/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.mchange_commons_java;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import com.mchange.lang.ObjectUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("deprecation")
public class ObjectUtilsTest {
    @Test
    void objectToByteArrayAndObjectFromByteArrayRoundTripJdkSerializableTypes() throws Exception {
        LinkedHashMap<String, Object> original = samplePayload();

        byte[] bytes = ObjectUtils.objectToByteArray(original);
        Object restored = ObjectUtils.objectFromByteArray(bytes);

        assertThat(bytes).isNotEmpty();
        assertThat(restored).isEqualTo(original);
    }

    private static LinkedHashMap<String, Object> samplePayload() {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", "alpha");
        payload.put("count", 7);
        payload.put("values", new ArrayList<>(List.of("one", "two", "three")));
        payload.put("flags", new ArrayList<>(List.of(Boolean.TRUE, Boolean.FALSE)));
        return payload;
    }
}
