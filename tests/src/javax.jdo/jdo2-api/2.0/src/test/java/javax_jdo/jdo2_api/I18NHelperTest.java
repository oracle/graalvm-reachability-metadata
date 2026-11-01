/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_jdo.jdo2_api;

import static org.assertj.core.api.Assertions.assertThat;

import javax.jdo.JDOHelper;
import javax.jdo.spi.I18NHelper;

import org.junit.jupiter.api.Test;

public class I18NHelperTest {
    @Test
    void locatesTheLibraryBundleFromAnApiClassAndFormatsParameters() {
        I18NHelper helper = I18NHelper.getInstance(JDOHelper.class);

        String message = helper.msg("EXC_GetPMFClassNotFound", "example.persistence.Factory");

        assertThat(message).contains("example.persistence.Factory").doesNotContain("{0}");
        assertThat(helper.getResourceBundle().containsKey("EXC_GetPMFClassNotFound")).isTrue();
    }

    @Test
    void formatsMultipleArgumentsUsingTheNamedBundle() {
        I18NHelper helper = I18NHelper.getInstance("javax.jdo.Bundle", JDOHelper.class.getClassLoader());

        String message = helper.msg("EXC_ObjectIdentityStringConstruction",
                "invalid decimal", "java.math.BigDecimal", "amount:west");

        assertThat(message).contains("invalid decimal", "java.math.BigDecimal", "amount:west")
                .doesNotContain("{0}", "{1}", "{2}");
        assertThat(helper.msg("EXC_SingleFieldIdentityNullParameter")).contains("null");
    }
}
