/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_yetus.audience_annotations;

import java.lang.annotation.Annotation;
import java.util.Locale;

import org.apache.yetus.audience.InterfaceAudience;
import org.apache.yetus.audience.InterfaceStability;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@InterfaceAudience.Public
@InterfaceStability.Stable
class Audience_annotationsTest {
    @Test
    void publicStableApiExecutesNormally() {
        GreetingService service = GreetingService.forPrefix("Hello");

        assertThat(service.greet("Yetus")).isEqualTo("Hello Yetus");
        assertThat(service.describe()).isEqualTo("public-stable:Hello");
    }

    @Test
    void limitedPrivateExtensionPointsSupportSingleAndMultipleConsumers() {
        GreetingService singleConsumerService = new GreetingService("Hi", new SurroundingFormatter("[single]"));
        GreetingService multiConsumerService = new GreetingService("Hi", new UpperCaseFormatter());

        assertThat(singleConsumerService.greet("docs")).isEqualTo("Hi [single]docs");
        assertThat(multiConsumerService.greet("docs")).isEqualTo("Hi DOCS");
    }

    @Test
    void privateUnstableTypesRemainUsableJavaTypes() {
        InternalCounter counter = new InternalCounter(2);

        assertThat(counter.increment()).isEqualTo(3);
        assertThat(counter.increment()).isEqualTo(4);
        assertThat(counter.current()).isEqualTo(4);
    }

    @Test
    void utilityHolderTypesAndLimitedPrivateContractsAreUsableWithoutReflection() {
        assertThat(InterfaceAudience.class).isNotNull();
        assertThat(new InterfaceStability()).isNotNull();

        InterfaceAudience.LimitedPrivate limitedPrivate = new InterfaceAudience.LimitedPrivate() {
            @Override
            public String[] value() {
                return new String[] { "analytics", "reporting" };
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return InterfaceAudience.LimitedPrivate.class;
            }
        };

        assertThat(limitedPrivate.value()).containsExactly("analytics", "reporting");
        assertThat(limitedPrivate.annotationType()).isSameAs(InterfaceAudience.LimitedPrivate.class);
    }

    @InterfaceAudience.LimitedPrivate("tests")
    @InterfaceStability.Evolving
    interface Formatter {
        @InterfaceAudience.LimitedPrivate({ "tests", "docs" })
        @InterfaceStability.Evolving
        String format(@InterfaceAudience.Public @InterfaceStability.Stable String value);
    }

    @InterfaceAudience.Public
    @InterfaceStability.Stable
    static final class GreetingService {
        @InterfaceAudience.Private
        @InterfaceStability.Unstable
        private final String prefix;

        @InterfaceAudience.LimitedPrivate({ "tests", "docs" })
        @InterfaceStability.Evolving
        private final Formatter formatter;

        @InterfaceAudience.Public
        @InterfaceStability.Evolving
        GreetingService(
                @InterfaceAudience.Public @InterfaceStability.Stable String prefix,
                @InterfaceAudience.LimitedPrivate("tests") @InterfaceStability.Evolving Formatter formatter) {
            this.prefix = prefix;
            this.formatter = formatter;
        }

        @InterfaceAudience.Public
        @InterfaceStability.Stable
        static GreetingService forPrefix(@InterfaceAudience.Public @InterfaceStability.Stable String prefix) {
            return new GreetingService(prefix, new IdentityFormatter());
        }

        @InterfaceAudience.Public
        @InterfaceStability.Stable
        String greet(@InterfaceAudience.Public @InterfaceStability.Stable String name) {
            @InterfaceAudience.Private
            @InterfaceStability.Unstable
            String separator = " ";
            return prefix + separator + formatter.format(name);
        }

        @InterfaceAudience.LimitedPrivate("tests")
        @InterfaceStability.Evolving
        String describe() {
            return "public-stable:" + prefix;
        }
    }

    @InterfaceAudience.LimitedPrivate("tests")
    @InterfaceStability.Evolving
    static final class IdentityFormatter implements Formatter {
        @Override
        public String format(String value) {
            return value;
        }
    }

    @InterfaceAudience.LimitedPrivate("docs")
    @InterfaceStability.Evolving
    static final class SurroundingFormatter implements Formatter {
        @InterfaceAudience.Private
        @InterfaceStability.Unstable
        private final String marker;

        @InterfaceAudience.LimitedPrivate("docs")
        @InterfaceStability.Evolving
        SurroundingFormatter(@InterfaceAudience.Public @InterfaceStability.Stable String marker) {
            this.marker = marker;
        }

        @Override
        public String format(String value) {
            return marker + value;
        }
    }

    @InterfaceAudience.LimitedPrivate({ "analytics", "reporting" })
    @InterfaceStability.Evolving
    static final class UpperCaseFormatter implements Formatter {
        @Override
        public String format(String value) {
            return value.toUpperCase(Locale.ROOT);
        }
    }

    @InterfaceAudience.Private
    @InterfaceStability.Unstable
    static final class InternalCounter {
        @InterfaceAudience.Private
        @InterfaceStability.Unstable
        private int value;

        @InterfaceAudience.Private
        @InterfaceStability.Unstable
        InternalCounter(@InterfaceAudience.Private @InterfaceStability.Unstable int initialValue) {
            this.value = initialValue;
        }

        @InterfaceAudience.Private
        @InterfaceStability.Unstable
        int increment() {
            return ++value;
        }

        @InterfaceAudience.Private
        @InterfaceStability.Unstable
        int current() {
            return value;
        }
    }
}
