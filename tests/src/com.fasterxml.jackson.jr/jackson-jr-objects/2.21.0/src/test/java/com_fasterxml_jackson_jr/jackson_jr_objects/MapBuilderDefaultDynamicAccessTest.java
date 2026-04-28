/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jr.jackson_jr_objects;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.jr.ob.JSON;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MapBuilderDefaultDynamicAccessTest {
    @BeforeEach
    void resetConstructorCalls() {
        CountsMap.resetConstructorCalls();
    }

    @Test
    void readsEmptyTypedMapSubclasses() throws Exception {
        CountsMap counts = JSON.std.beanFrom(CountsMap.class, "{}");

        assertThat(counts).isEmpty();
        assertThat(CountsMap.getConstructorCalls()).isEqualTo(1);
    }

    @Test
    void readsSingletonTypedMapSubclasses() throws Exception {
        CountsMap counts = JSON.std.beanFrom(CountsMap.class, "{\"only\":99}");

        assertThat(counts).containsEntry("only", 99);
        assertThat(CountsMap.getConstructorCalls()).isEqualTo(1);
    }

    @Test
    void readsTypedMapSubclassesInsideCollections() throws Exception {
        List<CountsMap> counts = JSON.std.listOfFrom(CountsMap.class,
                "[{\"alpha\":1},{\"beta\":2}]");

        assertThat(counts).hasSize(2);
        assertThat(counts).allSatisfy(count -> assertThat(count).isInstanceOf(CountsMap.class));
        assertThat(counts.get(0)).containsEntry("alpha", 1);
        assertThat(counts.get(1)).containsEntry("beta", 2);
        assertThat(CountsMap.getConstructorCalls()).isEqualTo(2);
    }

    public static final class CountsMap extends LinkedHashMap<String, Object> {
        private static final AtomicInteger CONSTRUCTOR_CALLS = new AtomicInteger();

        public CountsMap() {
            CONSTRUCTOR_CALLS.incrementAndGet();
        }

        static void resetConstructorCalls() {
            CONSTRUCTOR_CALLS.set(0);
        }

        static int getConstructorCalls() {
            return CONSTRUCTOR_CALLS.get();
        }
    }
}
