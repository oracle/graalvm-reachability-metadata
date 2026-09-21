/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import org.aspectj.lang.reflect.ConstructorSignature;
import org.aspectj.runtime.reflect.Factory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConstructorSignatureImplTest {
    @Test
    void resolvesDeclaredConstructorFromSignature() {
        Factory factory = new Factory("ConstructorSignatureImplTest.java", ConstructorSignatureImplTest.class);
        ConstructorSignature signature = factory.makeConstructorSig(
                Modifier.PUBLIC,
                ConstructorFixture.class,
                new Class[] {String.class, int.class},
                new String[] {"name", "amount"},
                new Class[0]
        );

        Constructor constructor = signature.getConstructor();

        assertThat(constructor).isNotNull();
        assertThat(constructor.getDeclaringClass()).isEqualTo(ConstructorFixture.class);
        assertThat(constructor.getParameterTypes()).containsExactly(String.class, int.class);
    }

    public static final class ConstructorFixture {
        public ConstructorFixture(String name, int amount) {
            assertThat(name).hasSize(amount);
        }
    }
}
