/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_aries_proxy.org_apache_aries_proxy_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.apache.aries.proxy.FinalModifierException;
import org.apache.aries.proxy.UnableToProxyException;
import org.junit.jupiter.api.Test;

public class Org_apache_aries_proxy_apiTest {
    @Test
    void finalModifierExceptionDescribesFinalClassesAndMethods() {
        Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.ROOT);

            FinalModifierException finalClass = new FinalModifierException(FinalService.class);
            assertThat(finalClass.isFinalClass()).isTrue();
            assertThat(finalClass.getFinalMethods()).isNull();
            assertThat(finalClass.getClassName()).isEqualTo(FinalService.class.getName());
            assertThat(finalClass.getMessage())
                    .isEqualTo("The class " + FinalService.class.getName() + " is final.");

            FinalModifierException finalMethods =
                    new FinalModifierException(ServiceWithFinalMethod.class, "finish()");
            assertThat(finalMethods.isFinalClass()).isFalse();
            assertThat(finalMethods.getFinalMethods()).isEqualTo("finish()");
            assertThat(finalMethods.getClassName()).isEqualTo(ServiceWithFinalMethod.class.getName());
            assertThat(finalMethods.getMessage())
                    .isEqualTo("The methods finish() in class "
                            + ServiceWithFinalMethod.class.getName()
                            + " are final.");
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    @Test
    void unableToProxyExceptionPreservesClassNamesAndCauses() {
        UnableToProxyException classOnly = new UnableToProxyException(ServiceWithFinalMethod.class);
        assertThat(classOnly.getClassName()).isEqualTo(ServiceWithFinalMethod.class.getName());
        assertThat(classOnly.getMessage()).isEqualTo(ServiceWithFinalMethod.class.getName());

        Exception classCause = new Exception("class cause");
        UnableToProxyException fromClass = new UnableToProxyException(ServiceWithFinalMethod.class, classCause);
        assertThat(fromClass.getClassName()).isEqualTo(ServiceWithFinalMethod.class.getName());
        assertThat(fromClass.getCause()).isSameAs(classCause);

        Throwable namedCause = new IllegalStateException("named cause");
        UnableToProxyException fromName = new UnableToProxyException("example.Service", namedCause);
        assertThat(fromName.getClassName()).isEqualTo("example.Service");
        assertThat(fromName.getCause()).isSameAs(namedCause);
    }

    public static final class FinalService {}

    public static class ServiceWithFinalMethod {
        public final void finish() { }
    }
}
