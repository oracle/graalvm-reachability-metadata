/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.internal.util.SerializationHelper;
import org.hibernate.internal.util.collections.ConcurrentReferenceHashMap;
import org.hibernate.internal.util.collections.ConcurrentReferenceHashMap.ReferenceType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConcurrentReferenceHashMapTest {

    @Test
    public void preservesStrongEntriesAcrossSerialization() {
        ConcurrentReferenceHashMap<String, String> map =
                new ConcurrentReferenceHashMap<>(8, ReferenceType.STRONG, ReferenceType.STRONG);
        map.put("entity", "Student");
        map.put("access", "reflection");

        ConcurrentReferenceHashMap<?, ?> copy =
                (ConcurrentReferenceHashMap<?, ?>) SerializationHelper.clone(map);

        assertThat(copy.get("entity")).isEqualTo("Student");
        assertThat(copy.get("access")).isEqualTo("reflection");
    }
}
