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

public class ClassCopierFactoryArrayImplAnonymous1Test {
    @Test
    void createsAnIndependentReferenceArray() {
        ArrayValue[] source = {new ArrayValue(3), new ArrayValue(5)};
        ObjectCopier copier = CopyobjectDefaults.makeReflectObjectCopierFactory().make();

        ArrayValue[] copy = (ArrayValue[]) copier.copy(source);

        assertThat(copy).isNotSameAs(source).hasSize(2);
        assertThat(copy[0]).isNotSameAs(source[0]);
        assertThat(copy[0].number).isEqualTo(3);
        assertThat(copy[1].number).isEqualTo(5);
    }

    public static final class ArrayValue {
        private int number;

        public ArrayValue() { }

        public ArrayValue(int number) {
            this.number = number;
        }
    }
}
