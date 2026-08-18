/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package hibernate_validator;

import java.util.Locale;
import java.util.ResourceBundle;

import org.hibernate.validator.resourceloading.PlatformResourceBundleLocator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PlatformResourceBundleLocatorTest {
    @Test
    void aggregatesValidatorResourceBundles() {
        PlatformResourceBundleLocator locator = new PlatformResourceBundleLocator(
                "org.hibernate.validator.ValidationMessages",
                PlatformResourceBundleLocatorTest.class.getClassLoader(),
                true
        );

        ResourceBundle bundle = locator.getResourceBundle(Locale.ENGLISH);

        assertThat(bundle.containsKey("jakarta.validation.constraints.NotBlank.message")).isTrue();
    }
}
