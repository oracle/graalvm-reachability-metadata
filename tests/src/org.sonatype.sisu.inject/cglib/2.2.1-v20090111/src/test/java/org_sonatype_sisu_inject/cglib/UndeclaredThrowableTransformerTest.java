/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu_inject.cglib;

import static org.assertj.core.api.Assertions.assertThat;

import net.sf.cglib.proxy.UndeclaredThrowableException;
import net.sf.cglib.transform.ClassTransformer;
import net.sf.cglib.transform.impl.UndeclaredThrowableTransformer;
import org.junit.jupiter.api.Test;

public class UndeclaredThrowableTransformerTest {

    @Test
    void createsTransformerForThrowableWrapper() {
        ClassTransformer transformer = new UndeclaredThrowableTransformer(UndeclaredThrowableException.class);

        assertThat(transformer).isInstanceOf(UndeclaredThrowableTransformer.class);
    }
}
