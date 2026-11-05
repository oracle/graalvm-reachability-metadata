/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_jdo.jdo2_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Date;
import java.util.Locale;

import javax.jdo.JDOUserException;
import javax.jdo.spi.JDOImplHelper;

import org.junit.jupiter.api.Test;

public class JDOImplHelperTest {
    @Test
    void constructsKeysUsingTheirPublicStringConstructor() {
        Object decimal = JDOImplHelper.construct(BigDecimal.class.getName(), "125.50");
        Object text = JDOImplHelper.construct(String.class.getName(), "account:east");

        assertThat(decimal).isEqualTo(new BigDecimal("125.50"));
        assertThat(text).isEqualTo("account:east");
    }

    @Test
    void constructsKeysUsingBuiltInStringAdapters() {
        Object currency = JDOImplHelper.construct(Currency.class.getName(), "USD");
        Object locale = JDOImplHelper.construct(Locale.class.getName(), "en_US_POSIX");
        Object date = JDOImplHelper.construct(Date.class.getName(), "1700000000123");

        assertThat(currency).isEqualTo(Currency.getInstance("USD"));
        assertThat(locale).isEqualTo(new Locale("en", "US", "POSIX"));
        assertThat(date).isEqualTo(new Date(1700000000123L));
    }

    @Test
    void rejectsInvalidCurrencyKeysWithAnAnnotatedCause() {
        assertThatThrownBy(() -> JDOImplHelper.construct(Currency.class.getName(), "not-a-currency"))
                .isInstanceOf(JDOUserException.class)
                .hasMessageContaining("not-a-currency")
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }
}
