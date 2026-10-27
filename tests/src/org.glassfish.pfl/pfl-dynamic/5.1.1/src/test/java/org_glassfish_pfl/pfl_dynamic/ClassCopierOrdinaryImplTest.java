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

public class ClassCopierOrdinaryImplTest {
    @Test
    void createsAndPopulatesAnIndependentObject() {
        OrdinaryValue source = new OrdinaryValue(42, "answer");
        ObjectCopier copier = CopyobjectDefaults.makeReflectObjectCopierFactory().make();

        OrdinaryValue copy = (OrdinaryValue) copier.copy(source);

        assertThat(copy).isNotSameAs(source);
        assertThat(copy.number).isEqualTo(42);
        assertThat(copy.text).isEqualTo("answer");
    }

    public static final class OrdinaryValue {
        private int number;
        private String text;

        public OrdinaryValue() { }

        public OrdinaryValue(int number, String text) {
            this.number = number;
            this.text = text;
        }
    }
}
