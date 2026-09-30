/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_pfl.pfl_dynamic;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.glassfish.pfl.dynamic.copyobject.spi.CopyobjectDefaults;
import org.glassfish.pfl.dynamic.copyobject.spi.ObjectCopier;
import org.junit.jupiter.api.Test;

public class JavaStreamObjectCopierImplTest {
    @Test
    void copiesASerializableObjectGraph() {
        StreamValue source = new StreamValue("root", new ArrayList<>(List.of("one", "two")));
        ObjectCopier copier = CopyobjectDefaults.makeJavaStreamObjectCopierFactory().make();

        StreamValue copy = (StreamValue) copier.copy(source);

        assertThat(copy).isNotSameAs(source);
        assertThat(copy.name).isEqualTo("root");
        assertThat(copy.values).containsExactly("one", "two").isNotSameAs(source.values);
    }

    public static final class StreamValue implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String name;
        private final List<String> values;

        public StreamValue(String name, List<String> values) {
            this.name = name;
            this.values = values;
        }
    }
}
