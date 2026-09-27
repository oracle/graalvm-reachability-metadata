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

public class ClassCopierOrdinaryImplInnerConstructorFactoryTest {
    @Test
    void copiesAClassWhoseNoArgConstructorComesFromItsParent() {
        ConstructorValue source = new ConstructorValue("constructed", 9);
        ObjectCopier copier = CopyobjectDefaults.makeReflectObjectCopierFactory().make();

        ConstructorValue copy = (ConstructorValue) copier.copy(source);

        assertThat(copy).isNotSameAs(source);
        assertThat(copy.parentText()).isEqualTo("constructed");
        assertThat(copy.number).isEqualTo(9);
    }

    public static class ConstructorParent {
        private String parentText;

        public ConstructorParent() {
            this.parentText = "default";
        }

        public ConstructorParent(String parentText) {
            this.parentText = parentText;
        }

        public String parentText() {
            return parentText;
        }
    }

    public static final class ConstructorValue extends ConstructorParent {
        private int number;

        public ConstructorValue(String parentText, int number) {
            super(parentText);
            this.number = number;
        }
    }
}
