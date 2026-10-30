/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib;

import net.sf.cglib.core.KeyFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class KeyFactoryTest {
    @Test
    void requiresFactoryMethodToReturnObject() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> KeyFactory.create(NumericKey.class))
                .withMessage("newInstance method must return Object");
    }

    @Test
    void requiresFactoryMethodNamedNewInstance() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> KeyFactory.create(NamedKey.class))
                .withMessageContaining("missing newInstance method");
    }

    public interface NumericKey {
        int newInstance(String value);
    }

    public interface NamedKey {
        Object key(String value);
    }
}
