/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_adapter_reflect;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.adapter.reflect.ClassLookup;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class ClassLookupTest {

    @Test
    void findsLoadableClassByFullyQualifiedName() {
        ClassLookup lookup = new ClassLookup();

        Optional<Class> result = lookup.forName(String.class.getName());

        assertThat(result).contains(String.class);
    }

    @Test
    void returnsEmptyOptionalForUnknownClassName() {
        ClassLookup lookup = new ClassLookup();

        Optional<Class> result = lookup.forName("example.missing.DoesNotExist");

        assertThat(result).isEmpty();
    }
}
