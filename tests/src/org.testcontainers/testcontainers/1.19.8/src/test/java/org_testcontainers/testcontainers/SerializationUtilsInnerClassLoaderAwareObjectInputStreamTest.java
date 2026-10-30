/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.lang3.SerializationUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class SerializationUtilsInnerClassLoaderAwareObjectInputStreamTest {
    @Test
    void deepClonesApplicationObjectsAndTheirMutableState() {
        Inventory inventory = new Inventory();
        ArrayList<Inventory> source = new ArrayList<>(List.of(inventory));
        ArrayList<Inventory> copy = SerializationUtils.clone(source);
        assertThat(copy).hasSize(1).isNotSameAs(source);
        Inventory cloned = copy.get(0);
        assertThat(cloned).isNotSameAs(inventory);
        assertThat(cloned.items).containsExactly("worker").isNotSameAs(inventory.items);
        cloned.items.add("queue");
        assertThat(inventory.items).containsExactly("worker");
    }

    public static class Inventory implements Serializable {
        private static final long serialVersionUID = 1L;
        private final ArrayList<String> items = new ArrayList<>(List.of("worker"));
    }
}
