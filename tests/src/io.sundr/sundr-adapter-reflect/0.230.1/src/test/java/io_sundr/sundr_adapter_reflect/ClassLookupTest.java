/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_adapter_reflect;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.adapter.reflect.ClassLookup;
import org.junit.jupiter.api.Test;

public class ClassLookupTest {

    @Test
    void resolvesAnAvailableClassByFullyQualifiedName() {
        ClassLookup lookup = new ClassLookup();

        assertThat(lookup.forName(String.class.getName())).containsSame(String.class);
    }
}
