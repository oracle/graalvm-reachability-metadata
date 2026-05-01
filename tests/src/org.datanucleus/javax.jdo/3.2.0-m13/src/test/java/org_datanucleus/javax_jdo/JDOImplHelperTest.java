/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_datanucleus.javax_jdo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;

import javax.jdo.JDOUserException;
import javax.jdo.spi.JDOImplHelper;

import org.junit.jupiter.api.Test;

public class JDOImplHelperTest {
    @Test
    void constructUsesPublicStringConstructorWhenNoSpecialConstructorRegistered() {
        String keyString = "native-ready-key";

        Object constructed = JDOImplHelper.construct(String.class.getName(), keyString);

        assertThat(constructed).isInstanceOf(String.class).isEqualTo(keyString);
    }

    @Test
    void constructUsesRegisteredStringConstructorForLocaleIdentityStrings() {
        Object constructed = JDOImplHelper.construct(Locale.class.getName(), "en_US_POSIX");

        assertThat(constructed).isEqualTo(new Locale("en", "US", "POSIX"));
    }

    @Test
    void constructWrapsClassesWithoutStringConstructors() {
        assertThatThrownBy(() -> JDOImplHelper.construct(Object.class.getName(), "ignored"))
                .isInstanceOf(JDOUserException.class)
                .hasCauseInstanceOf(NoSuchMethodException.class);
    }
}
