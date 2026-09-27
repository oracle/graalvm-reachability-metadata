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

public class DefaultClassCopierFactoriesAnonymous1Test {
    @Test
    void acceptsAnOrdinaryClassAndCopiesIt() {
        CopyableValue source = new CopyableValue("copyable");
        ObjectCopier copier = CopyobjectDefaults.makeReflectObjectCopierFactory().make();

        CopyableValue copy = (CopyableValue) copier.copy(source);

        assertThat(copy).isNotSameAs(source);
        assertThat(copy.value).isEqualTo("copyable");
    }

    public static final class CopyableValue {
        private String value;

        public CopyableValue() {}

        public CopyableValue(String value) {
            this.value = value;
        }
    }
}
