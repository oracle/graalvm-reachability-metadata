/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_fastinfoset.FastInfoset;

import com.sun.xml.fastinfoset.CommonResourceBundle;
import java.util.Locale;
import java.util.ResourceBundle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommonResourceBundleTest {

    @Test
    void loadsCommonResourceBundlesThroughAllPublicAccessors() {
        CommonResourceBundle defaultBundle = CommonResourceBundle.getInstance();

        assertThat(defaultBundle.getBundle().getBaseBundleName())
            .isEqualTo(CommonResourceBundle.BASE_NAME);
        assertThat(defaultBundle.getBundle().getString("message.illegalState"))
            .isEqualTo("Illegal state for decoding of EncodedCharacterString");

        CommonResourceBundle localizedBundle = CommonResourceBundle.getInstance(Locale.JAPANESE);

        assertThat(localizedBundle.getBundle().getBaseBundleName())
            .isEqualTo(CommonResourceBundle.BASE_NAME);
        assertThat(localizedBundle.getBundle().getString("message.illegalState"))
            .isEqualTo("Illegal state for decoding of EncodedCharacterString");

        ResourceBundle directLocalizedBundle = localizedBundle.getBundle(Locale.GERMAN);

        assertThat(directLocalizedBundle.getBaseBundleName())
            .isEqualTo(CommonResourceBundle.BASE_NAME);
        assertThat(directLocalizedBundle.getString("message.illegalState"))
            .isEqualTo("Illegal state for decoding of EncodedCharacterString");
    }
}
