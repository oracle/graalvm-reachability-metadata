/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package hibernate_validator;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotBlank;

import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.HibernateValidatorConfiguration;
import org.hibernate.validator.spi.messageinterpolation.LocaleResolver;
import org.hibernate.validator.spi.messageinterpolation.LocaleResolverContext;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class LoadClassTest {
    private static final String LOCALE_RESOLVER_CLASSNAME = HibernateValidatorConfiguration.LOCALE_RESOLVER_CLASSNAME;

    @Test
    void loadsValidatorNamespaceClassFromConfigurationProperty() {
        try (ValidatorFactory factory = Validation.byProvider(HibernateValidator.class)
                .configure()
                .addProperty(LOCALE_RESOLVER_CLASSNAME,
                        "org.hibernate.validator.internal.engine.messageinterpolation.DefaultLocaleResolver")
                .buildValidatorFactory()) {
            assertDefaultInterpolation(factory.getValidator());
        }
    }

    @Test
    void reportsValidatorNamespaceClassConfiguredButUnavailable() {
        HibernateValidatorConfiguration configuration = Validation.byProvider(HibernateValidator.class)
                .configure()
                .addProperty(LOCALE_RESOLVER_CLASSNAME, "org.hibernate.validator.NoSuchLocaleResolver");

        assertThatThrownBy(configuration::buildValidatorFactory)
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("org.hibernate.validator.NoSuchLocaleResolver");
    }

    @Test
    void loadsConfiguredClassFromExplicitExternalClassLoader() {
        try (ValidatorFactory factory = Validation.byProvider(HibernateValidator.class)
                .configure()
                .externalClassLoader(LoadClassTest.class.getClassLoader())
                .addProperty(LOCALE_RESOLVER_CLASSNAME, ConfiguredLocaleResolver.class.getName())
                .buildValidatorFactory()) {
            assertDefaultInterpolation(factory.getValidator());
        }
    }

    @Test
    void loadsConfiguredClassFromThreadContextClassLoader() {
        try (ValidatorFactory factory = Validation.byProvider(HibernateValidator.class)
                .configure()
                .addProperty(LOCALE_RESOLVER_CLASSNAME, ConfiguredLocaleResolver.class.getName())
                .buildValidatorFactory()) {
            assertDefaultInterpolation(factory.getValidator());
        }
    }

    @Test
    void loadsConfiguredClassFromHibernateValidatorClassLoaderWhenThreadContextClassLoaderIsUnavailable() {
        HibernateValidatorConfiguration configuration = Validation.byProvider(HibernateValidator.class)
                .configure()
                .addProperty(LOCALE_RESOLVER_CLASSNAME, ConfiguredLocaleResolver.class.getName());
        Thread thread = Thread.currentThread();
        ClassLoader originalContextClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(null);
        try (ValidatorFactory factory = configuration.buildValidatorFactory()) {
            assertDefaultInterpolation(factory.getValidator());
        }
        finally {
            thread.setContextClassLoader(originalContextClassLoader);
        }
    }

    private static void assertDefaultInterpolation(Validator validator) {
        Set<ConstraintViolation<Form>> violations = validator.validate(new Form(""));

        assertThat(violations).singleElement().satisfies(violation -> {
            assertThat(violation.getPropertyPath()).hasToString("name");
            assertThat(violation.getMessage()).isEqualTo("name is required");
        });
    }

    public static final class ConfiguredLocaleResolver implements LocaleResolver {
        public ConfiguredLocaleResolver() {
        }

        @Override
        public Locale resolve(LocaleResolverContext context) {
            return Locale.US;
        }
    }

    private static final class Form {
        @NotBlank(message = "${validatedValue == '' ? 'name is required' : 'unexpected value'}")
        private final String name;

        private Form(String name) {
            this.name = name;
        }
    }
}
