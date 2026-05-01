/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_beanutils.commons_beanutils_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.apache.commons.beanutils.locale.LocaleConvertUtilsBean;
import org.junit.jupiter.api.Test;

public class LocaleConvertUtilsBeanTest {

    @Test
    void constructorRegistersDefaultLocaleConverters() {
        LocaleConvertUtilsBean convertUtilsBean = new LocaleConvertUtilsBean();

        assertThat(convertUtilsBean.lookup(String.class, Locale.getDefault())).isNotNull();
        assertThat(convertUtilsBean.lookup(Integer.class, Locale.getDefault())).isNotNull();
    }

    @Test
    void convertsStringValuesToRequestedWrapperArrayType() {
        LocaleConvertUtilsBean convertUtilsBean = new LocaleConvertUtilsBean();

        Object converted = convertUtilsBean.convert(new String[] { "1", "2", "3" }, Integer[].class, Locale.US, null);

        assertThat(converted).isInstanceOf(Integer[].class);
        assertThat((Integer[]) converted).containsExactly(1, 2, 3);
    }

    @Test
    void convertsStringValuesToRequestedPrimitiveArrayType() {
        LocaleConvertUtilsBean convertUtilsBean = new LocaleConvertUtilsBean();

        Object converted = convertUtilsBean.convert(new String[] { "4", "5", "6" }, Integer.TYPE, Locale.US, null);

        assertThat(converted).isInstanceOf(int[].class);
        assertThat((int[]) converted).containsExactly(4, 5, 6);
    }
}
