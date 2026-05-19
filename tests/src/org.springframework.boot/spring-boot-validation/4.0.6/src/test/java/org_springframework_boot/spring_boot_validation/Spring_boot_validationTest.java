/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_validation;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;

import org.springframework.aop.support.AopUtils;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.boot.validation.autoconfigure.ValidationConfigurationCustomizer;
import org.springframework.boot.validation.autoconfigure.ValidatorAdapter;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Validator;
import org.springframework.validation.annotation.Validated;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;
import org.springframework.validation.method.MethodValidationException;
import org.springframework.validation.method.ParameterValidationResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Spring_boot_validationTest {

    @Test
    void autoConfiguredValidatorUsesApplicationMessageSource() {
        try (AnnotationConfigApplicationContext context = contextWith(MessageSourceConfiguration.class)) {
            jakarta.validation.Validator jakartaValidator = context.getBean(jakarta.validation.Validator.class);

            Set<ConstraintViolation<RegistrationForm>> violations = jakartaValidator.validate(new RegistrationForm(""));

            assertThat(jakartaValidator).isInstanceOf(LocalValidatorFactoryBean.class);
            assertThat(context.getBeanNamesForType(MethodValidationPostProcessor.class)).hasSize(1);
            assertThat(violations).singleElement()
                    .satisfies((violation) -> {
                        assertThat(violation.getPropertyPath().toString()).isEqualTo("name");
                        assertThat(violation.getMessage()).isEqualTo("A registration name is required");
                    });
        }
    }

    @Test
    void configurationCustomizersAreAppliedToDefaultValidatorFactory() {
        ClockProviderConfiguration.reset();
        try (AnnotationConfigApplicationContext context = contextWith(ClockProviderConfiguration.class)) {
            jakarta.validation.Validator validator = context.getBean(jakarta.validation.Validator.class);

            Set<ConstraintViolation<DeadlineForm>> violations = validator.validate(
                    new DeadlineForm(Instant.parse("2029-12-31T00:00:00Z")));

            assertThat(ClockProviderConfiguration.getCustomizationCount()).isEqualTo(1);
            assertThat(violations).singleElement()
                    .satisfies((violation) -> assertThat(violation.getPropertyPath().toString()).isEqualTo("deadline"));
        }
    }

    @Test
    void methodValidationPostProcessorRejectsInvalidMethodArguments() {
        try (AnnotationConfigApplicationContext context = contextWithProperties(
                Map.of("spring.aop.proxy-target-class", (Object) "false"), MethodValidationConfiguration.class)) {
            GreetingService service = context.getBean(GreetingService.class);

            assertThat(AopUtils.isAopProxy(service)).isTrue();
            assertThat(service.greet("Spring")).isEqualTo("Hello Spring");
            assertThatThrownBy(() -> service.greet(""))
                    .isInstanceOf(ConstraintViolationException.class)
                    .hasMessageContaining("must not be blank");
        }
    }

    @Test
    void methodValidationPostProcessorCanAdaptConstraintViolations() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment()
                    .getPropertySources()
                    .addFirst(new MapPropertySource("test", Map.of(
                            "spring.aop.proxy-target-class", (Object) "false",
                            "spring.validation.method.adapt-constraint-violations", "true")));
            context.register(ValidationAutoConfiguration.class, MethodValidationConfiguration.class);
            context.refresh();
            GreetingService service = context.getBean(GreetingService.class);

            assertThatThrownBy(() -> service.greet(""))
                    .isInstanceOf(MethodValidationException.class)
                    .satisfies((ex) -> {
                        MethodValidationException validationException = (MethodValidationException) ex;
                        assertThat(validationException.getParameterValidationResults())
                                .singleElement()
                                .extracting(ParameterValidationResult::getArgument)
                                .isEqualTo("");
                    });
        }
    }

    @Test
    void validatorAdapterWrapsJakartaValidatorAndDelegatesSpringValidation() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.refresh();
            LocalValidatorFactoryBean target = new LocalValidatorFactoryBean();
            target.afterPropertiesSet();
            try {
                Validator adapter = ValidatorAdapter.get(context, target);
                BeanPropertyBindingResult errors = new BeanPropertyBindingResult(new PersonForm("", 12), "personForm");

                adapter.validate(new PersonForm("", 12), errors);

                assertThat(adapter).isInstanceOf(ValidatorAdapter.class);
                assertThat(adapter.supports(PersonForm.class)).isTrue();
                assertThat(errors.getFieldErrors()).hasSize(2);
                assertThat(errors.getFieldError("name").getDefaultMessage()).isEqualTo("must not be blank");
                assertThat(errors.getFieldError("age").getDefaultMessage())
                        .isEqualTo("must be greater than or equal to 18");
                assertThat(((ValidatorAdapter) adapter).getTarget()).isSameAs(target);
                assertThat(((ValidatorAdapter) adapter).unwrap(LocalValidatorFactoryBean.class)).isSameAs(target);
            }
            finally {
                target.destroy();
            }
        }
    }

    @Test
    void validatorAdapterFindsExistingJakartaValidatorBeanInApplicationContext() {
        try (AnnotationConfigApplicationContext context = contextWith(ExistingJakartaValidatorConfiguration.class)) {
            Validator adapter = ValidatorAdapter.get(context, null);
            BeanPropertyBindingResult errors = new BeanPropertyBindingResult(new PersonForm("", 18), "personForm");

            adapter.validate(new PersonForm("", 18), errors);

            assertThat(adapter).isInstanceOf(ValidatorAdapter.class);
            assertThat(errors.getFieldError("name").getDefaultMessage()).isEqualTo("must not be blank");
            assertThat(((ValidatorAdapter) adapter).unwrap(jakarta.validation.Validator.class))
                    .isSameAs(context.getBean(jakarta.validation.Validator.class));
        }
    }

    private static AnnotationConfigApplicationContext contextWith(Class<?>... configurationClasses) {
        return contextWithProperties(Map.of(), configurationClasses);
    }

    private static AnnotationConfigApplicationContext contextWithProperties(Map<String, Object> properties,
            Class<?>... configurationClasses) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        if (!properties.isEmpty()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
        }
        context.register(ValidationAutoConfiguration.class);
        context.register(configurationClasses);
        try {
            context.refresh();
        }
        catch (RuntimeException ex) {
            context.close();
            throw ex;
        }
        return context;
    }

    @Configuration(proxyBeanMethods = false)
    public static class MessageSourceConfiguration {

        @Bean
        StaticMessageSource messageSource() {
            StaticMessageSource messageSource = new StaticMessageSource();
            messageSource.addMessage("registration.name.required", Locale.ENGLISH, "A registration name is required");
            messageSource.addMessage("registration.name.required", Locale.getDefault(),
                    "A registration name is required");
            return messageSource;
        }

    }

    @Configuration(proxyBeanMethods = false)
    public static class ClockProviderConfiguration {

        private static final AtomicInteger CUSTOMIZATION_COUNT = new AtomicInteger();

        static void reset() {
            CUSTOMIZATION_COUNT.set(0);
        }

        static int getCustomizationCount() {
            return CUSTOMIZATION_COUNT.get();
        }

        @Bean
        ValidationConfigurationCustomizer fixedClockCustomizer() {
            return (configuration) -> {
                CUSTOMIZATION_COUNT.incrementAndGet();
                configuration.clockProvider(() -> Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC));
            };
        }

    }

    @Configuration(proxyBeanMethods = false)
    public static class MethodValidationConfiguration {

        @Bean
        GreetingService greetingService() {
            return new DefaultGreetingService();
        }

    }

    @Configuration(proxyBeanMethods = false)
    public static class ExistingJakartaValidatorConfiguration {

        @Bean(destroyMethod = "close")
        ValidatorFactory validatorFactory() {
            return Validation.buildDefaultValidatorFactory();
        }

        @Bean
        jakarta.validation.Validator jakartaValidator(ValidatorFactory validatorFactory) {
            return validatorFactory.getValidator();
        }

    }

    public interface GreetingService {

        String greet(@NotBlank String name);

    }

    @Validated
    public static class DefaultGreetingService implements GreetingService {

        @Override
        public String greet(String name) {
            return "Hello " + name;
        }

    }

    public static class RegistrationForm {

        @NotBlank(message = "{registration.name.required}")
        private final String name;

        RegistrationForm(String name) {
            this.name = name;
        }

        public String getName() {
            return this.name;
        }

    }

    public static class DeadlineForm {

        @Future
        private final Instant deadline;

        DeadlineForm(Instant deadline) {
            this.deadline = deadline;
        }

        public Instant getDeadline() {
            return this.deadline;
        }

    }

    public static class PersonForm {

        @NotBlank
        private final String name;

        @Min(18)
        private final int age;

        PersonForm(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() {
            return this.name;
        }

        public int getAge() {
            return this.age;
        }

    }

}
