/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_pfl.pfl_dynamic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.glassfish.pfl.dynamic.copyobject.spi.CopyobjectDefaults;
import org.glassfish.pfl.dynamic.copyobject.spi.ObjectCopier;
import org.junit.jupiter.api.Test;

public class DefaultClassCopiersAnonymous3Test {
    @Test
    void createsAndPopulatesAnIndependentMap() {
        Map<String, String> source = new LinkedHashMap<>();
        source.put("first", "one");
        source.put("second", "two");
        ObjectCopier copier = CopyobjectDefaults.makeReflectObjectCopierFactory().make();

        Map<?, ?> copy = (Map<?, ?>) copier.copy(source);

        assertThat(copy).isNotSameAs(source).hasSize(2);
        assertThat(copy.get("first")).isEqualTo("one");
        assertThat(copy.get("second")).isEqualTo("two");
    }
}
