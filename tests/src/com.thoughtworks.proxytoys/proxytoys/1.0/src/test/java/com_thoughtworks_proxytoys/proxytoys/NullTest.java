/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_proxytoys.proxytoys;

import static org.assertj.core.api.Assertions.assertThat;

import com.thoughtworks.proxy.toys.nullobject.Null;
import org.junit.jupiter.api.Test;

public class NullTest {
    @Test
    public void buildsEmptyArrayForArrayTypes() {
        String[] names = Null.proxy(String[].class).build();

        assertThat(names).isEmpty();
        assertThat(names).isExactlyInstanceOf(String[].class);
        assertThat(Null.isNullObject(names)).isFalse();
    }
}
