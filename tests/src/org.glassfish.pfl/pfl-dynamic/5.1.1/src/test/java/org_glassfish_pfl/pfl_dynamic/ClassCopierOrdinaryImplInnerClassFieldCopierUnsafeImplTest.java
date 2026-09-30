/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_pfl.pfl_dynamic;

import static org.assertj.core.api.Assertions.assertThat;

import org.glassfish.pfl.dynamic.copyobject.spi.CopyobjectDefaults;
import org.glassfish.pfl.dynamic.copyobject.spi.ObjectCopier;
import org.junit.jupiter.api.Test;

public class ClassCopierOrdinaryImplInnerClassFieldCopierUnsafeImplTest {
    @Test
    void copiesAllInstanceFieldKinds() {
        FieldValue source = new FieldValue(7, 12L, true, "payload");
        ObjectCopier copier = CopyobjectDefaults.makeReflectObjectCopierFactory().make();

        FieldValue copy = (FieldValue) copier.copy(source);

        assertThat(copy).isNotSameAs(source);
        assertThat(copy.count).isEqualTo(7);
        assertThat(copy.total).isEqualTo(12L);
        assertThat(copy.enabled).isTrue();
        assertThat(copy.payload).isEqualTo("payload");
    }

    public static final class FieldValue {
        private int count;
        private long total;
        private boolean enabled;
        private Object payload;

        public FieldValue() { }

        public FieldValue(int count, long total, boolean enabled, Object payload) {
            this.count = count;
            this.total = total;
            this.enabled = enabled;
            this.payload = payload;
        }
    }
}
