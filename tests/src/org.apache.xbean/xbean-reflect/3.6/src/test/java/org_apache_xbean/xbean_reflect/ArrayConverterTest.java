/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_xbean.xbean_reflect;

import org.apache.xbean.propertyeditor.ArrayConverter;
import org.apache.xbean.propertyeditor.StringEditor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayConverterTest {
    @Test
    void createsArrayFromDelimitedText() {
        ArrayConverter converter = new ArrayConverter(String[].class, new StringEditor());

        Object value = converter.toObject("[alpha, beta, gamma]");

        assertThat(value).isInstanceOf(String[].class);
        assertThat((String[]) value).containsExactly("alpha", "beta", "gamma");
    }
}
