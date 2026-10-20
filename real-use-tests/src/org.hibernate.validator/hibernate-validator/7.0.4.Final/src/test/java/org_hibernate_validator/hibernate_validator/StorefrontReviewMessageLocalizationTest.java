/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_validator.hibernate_validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.MessageInterpolator;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.metadata.ConstraintDescriptor;
import java.util.List;
import java.util.ListResourceBundle;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;
import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.messageinterpolation.ResourceBundleMessageInterpolator;
import org.hibernate.validator.spi.messageinterpolation.LocaleResolver;
import org.hibernate.validator.spi.messageinterpolation.LocaleResolverContext;
import org.hibernate.validator.spi.resourceloading.ResourceBundleLocator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Localized message interpolation: a `LocaleResolver` choosing the message locale from the
 * request's `Accept-Language` header among the supported locales, an application
 * `ResourceBundleLocator` whose bundles take precedence over the built-in translations, resolve
 * nested message keys and fall back to the base bundle for untranslated keys, escaped braces,
 * re-rendering stored violations in an explicit locale through the factory's
 * `MessageInterpolator`, and the configuration-level `locales()`/`localeResolver()` settings
 * selecting Hibernate Validator's own translations.
 *
 * A multilingual storefront validates product reviews submitted through its web API and returns
 * the errors in the shopper's language. The storefront ships its own wording for the review
 * form, overrides one built-in message for German shoppers and relies on Hibernate Validator's
 * translations for everything else.
 */
public class StorefrontReviewMessageLocalizationTest {

    private static final Locale BRAZILIAN_PORTUGUESE = Locale.forLanguageTag("pt-BR");
    private static final Locale SPANISH = Locale.forLanguageTag("es");

    private ValidatorFactory factory;
    private Validator validator;

    @BeforeEach
    void createValidator() {
        MessageInterpolator interpolator = new ResourceBundleMessageInterpolator(
                new StorefrontMessages(),
                Set.of(Locale.ENGLISH, Locale.GERMAN, Locale.FRENCH),
                Locale.ENGLISH,
                new AcceptLanguageLocaleResolver(),
                false);
        factory = Validation.byProvider(HibernateValidator.class)
                .configure()
                .messageInterpolator(interpolator)
                .buildValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterEach
    void closeFactory() {
        factory.close();
    }

    @Test
    void acceptsCompleteReview() {
        Set<ConstraintViolation<ProductReview>> violations = RequestLocale.withAcceptLanguage(
                "de-DE,de;q=0.9", () -> validator.validate(validReview()));

        assertTrue(violations.isEmpty(), () -> "Unexpected violations: " + violations);
    }

    @Test
    void reportsEnglishMessagesFromApplicationAndBuiltInBundles() {
        Map<String, String> messages = RequestLocale.withAcceptLanguage(
                "en-US,en;q=0.8", () -> messagesByPath(validator.validate(invalidReview())));

        assertEquals(Map.of(
                "author", "must not be blank",
                "headline", "must not contain template markers such as {name}",
                "rating", "must be greater than or equal to 1",
                "sku", "article number must look like ABC-1234",
                "text", "size must be between 10 and 500"), messages);
    }

    @Test
    void prefersApplicationTranslationsOverBuiltInGermanMessages() {
        Map<String, String> messages = RequestLocale.withAcceptLanguage(
                "de-CH,de;q=0.9,en;q=0.5", () -> messagesByPath(validator.validate(invalidReview())));

        assertEquals(Map.of(
                "author", "bitte ausf\u00fcllen",
                "headline", "darf keine Platzhalter wie {name} enthalten",
                "rating", "muss gr\u00f6\u00dfer-gleich 1 sein",
                "sku", "Artikelnummer muss wie ABC-1234 aussehen",
                "text", "Gr\u00f6\u00dfe muss zwischen 10 und 500 sein"), messages);
    }

    @Test
    void fallsBackToBaseBundleForUntranslatedFrenchKey() {
        Map<String, String> messages = RequestLocale.withAcceptLanguage(
                "fr-CA,fr;q=0.9", () -> messagesByPath(validator.validate(invalidReview())));

        assertEquals(Map.of(
                "author", "ne doit pas \u00eatre vide",
                "headline", "must not contain template markers such as {name}",
                "rating", "doit \u00eatre sup\u00e9rieur ou \u00e9gal \u00e0 1",
                "sku", "la r\u00e9f\u00e9rence article doit ressembler \u00e0 ABC-1234",
                "text", "la taille doit \u00eatre comprise entre 10 et 500"), messages);
    }

    @Test
    void usesDefaultLocaleForUnsupportedOrMissingLanguages() {
        Map<String, String> japanese = RequestLocale.withAcceptLanguage(
                "ja-JP", () -> messagesByPath(validator.validate(invalidReview())));
        Map<String, String> noHeader = RequestLocale.withAcceptLanguage(
                null, () -> messagesByPath(validator.validate(invalidReview())));

        assertEquals("article number must look like ABC-1234", japanese.get("sku"));
        assertEquals("must not be blank", japanese.get("author"));
        assertEquals(japanese, noHeader);
    }

    @Test
    void rendersStoredViolationsInTheAuthorsProfileLocale() {
        Set<ConstraintViolation<ProductReview>> violations = RequestLocale.withAcceptLanguage(
                "en", () -> validator.validate(invalidReview()));
        MessageInterpolator interpolator = factory.getMessageInterpolator();

        Map<String, String> germanMail = new TreeMap<>();
        Map<String, String> frenchMail = new TreeMap<>();
        for (ConstraintViolation<ProductReview> violation : violations) {
            StoredViolationContext context = new StoredViolationContext(violation);
            String path = violation.getPropertyPath().toString();
            germanMail.put(path, interpolator.interpolate(violation.getMessageTemplate(), context, Locale.GERMAN));
            frenchMail.put(path, interpolator.interpolate(violation.getMessageTemplate(), context, Locale.FRENCH));
        }

        assertEquals("{shop.review.sku}", messageTemplateOf(violations, "sku"));
        assertEquals("Artikelnummer muss wie ABC-1234 aussehen", germanMail.get("sku"));
        assertEquals("Gr\u00f6\u00dfe muss zwischen 10 und 500 sein", germanMail.get("text"));
        assertEquals("bitte ausf\u00fcllen", germanMail.get("author"));
        assertEquals("la taille doit \u00eatre comprise entre 10 et 500", frenchMail.get("text"));
        assertEquals("doit \u00eatre sup\u00e9rieur ou \u00e9gal \u00e0 1", frenchMail.get("rating"));
        assertEquals(5, frenchMail.size());
    }

    @Test
    void configurationLocalesSelectBuiltInTranslations() {
        try (ValidatorFactory builtInFactory = Validation.byProvider(HibernateValidator.class)
                .configure()
                .locales(Locale.ENGLISH, BRAZILIAN_PORTUGUESE, SPANISH)
                .defaultLocale(Locale.ENGLISH)
                .localeResolver(new AcceptLanguageLocaleResolver())
                .buildValidatorFactory()) {
            Validator builtInValidator = builtInFactory.getValidator();
            ProductReview review = validReview();
            review.author = " ";
            review.rating = 0;

            Map<String, String> portuguese = RequestLocale.withAcceptLanguage(
                    "pt-BR,pt;q=0.9", () -> messagesByPath(builtInValidator.validate(review)));
            Map<String, String> spanish = RequestLocale.withAcceptLanguage(
                    "es-MX,es;q=0.8", () -> messagesByPath(builtInValidator.validate(review)));
            Map<String, String> fallback = RequestLocale.withAcceptLanguage(
                    "it-IT", () -> messagesByPath(builtInValidator.validate(review)));

            assertEquals(Map.of(
                    "author", "n\u00e3o deve estar em branco",
                    "rating", "deve ser maior que ou igual \u00e0 1"), portuguese);
            assertEquals(Map.of(
                    "author", "no debe estar vac\u00edo",
                    "rating", "debe ser mayor que o igual a 1"), spanish);
            assertEquals(Map.of(
                    "author", "must not be blank",
                    "rating", "must be greater than or equal to 1"), fallback);
        }
    }

    private static ProductReview validReview() {
        ProductReview review = new ProductReview();
        review.author = "Mila";
        review.headline = "Sturdy and quiet";
        review.text = "The grinder has run every morning for a month.";
        review.rating = 5;
        review.sku = "GRD-2041";
        return review;
    }

    private static ProductReview invalidReview() {
        ProductReview review = new ProductReview();
        review.author = "";
        review.headline = "Hello {name}";
        review.text = "Meh.";
        review.rating = 0;
        review.sku = "grinder";
        return review;
    }

    private static <T> Map<String, String> messagesByPath(Set<ConstraintViolation<T>> violations) {
        Map<String, String> messages = new TreeMap<>();
        for (ConstraintViolation<T> violation : violations) {
            String previous = messages.put(violation.getPropertyPath().toString(), violation.getMessage());
            assertNull(previous, () -> "Two violations on " + violation.getPropertyPath());
        }
        return messages;
    }

    private static <T> String messageTemplateOf(Set<ConstraintViolation<T>> violations, String path) {
        return violations.stream()
                .filter(violation -> violation.getPropertyPath().toString().equals(path))
                .findFirst()
                .orElseThrow()
                .getMessageTemplate();
    }

    /** A review submitted through the storefront API; each property fails at most one constraint. */
    public static class ProductReview {

        @NotBlank
        String author;

        @Pattern(regexp = "[^{}]*", message = "{shop.review.headline}")
        String headline;

        @Size(min = 10, max = 500)
        String text;

        @Min(1)
        int rating;

        @Pattern(regexp = "[A-Z]{3}-\\d{4}", message = "{shop.review.sku}")
        String sku;
    }

    /** Holds the `Accept-Language` header of the request being served on this thread. */
    static final class RequestLocale {

        private static final ThreadLocal<List<Locale.LanguageRange>> RANGES = new ThreadLocal<>();

        private RequestLocale() {
        }

        static <T> T withAcceptLanguage(String header, Supplier<T> action) {
            RANGES.set(header == null ? List.of() : Locale.LanguageRange.parse(header));
            try {
                return action.get();
            } finally {
                RANGES.remove();
            }
        }

        static List<Locale.LanguageRange> current() {
            List<Locale.LanguageRange> ranges = RANGES.get();
            return ranges == null ? List.of() : ranges;
        }
    }

    /** Picks the best supported locale for the current request, or the default locale. */
    static final class AcceptLanguageLocaleResolver implements LocaleResolver {

        @Override
        public Locale resolve(LocaleResolverContext context) {
            Locale match = Locale.lookup(RequestLocale.current(), context.getSupportedLocales());
            return match == null ? context.getDefaultLocale() : match;
        }
    }

    /** The storefront's own messages: an English base bundle with German and French children. */
    static final class StorefrontMessages implements ResourceBundleLocator {

        private final ResourceBundle base = new Bundle(new Object[][] {
            {"shop.term.sku", "article number"},
            {"shop.review.sku", "{shop.term.sku} must look like ABC-1234"},
            {"shop.review.headline", "must not contain template markers such as \\{name\\}"}
        }, null);

        private final Map<String, ResourceBundle> translations = Map.of(
                "de", new Bundle(new Object[][] {
                    {"shop.term.sku", "Artikelnummer"},
                    {"shop.review.sku", "{shop.term.sku} muss wie ABC-1234 aussehen"},
                    {"shop.review.headline", "darf keine Platzhalter wie \\{name\\} enthalten"},
                    {"jakarta.validation.constraints.NotBlank.message", "bitte ausf\u00fcllen"}
                }, base),
                "fr", new Bundle(new Object[][] {
                    {"shop.term.sku", "r\u00e9f\u00e9rence article"},
                    {"shop.review.sku", "la {shop.term.sku} doit ressembler \u00e0 ABC-1234"}
                }, base));

        @Override
        public ResourceBundle getResourceBundle(Locale locale) {
            return translations.getOrDefault(locale.getLanguage(), base);
        }

        /** An in-memory bundle chained to its parent, as `ResourceBundle.getBundle` would chain it. */
        static final class Bundle extends ListResourceBundle {

            private final Object[][] contents;

            Bundle(Object[][] contents, ResourceBundle parent) {
                this.contents = contents;
                if (parent != null) {
                    setParent(parent);
                }
            }

            @Override
            protected Object[][] getContents() {
                return contents;
            }
        }
    }

    /** Re-creates the interpolation context of a violation that was stored for later delivery. */
    static final class StoredViolationContext implements MessageInterpolator.Context {

        private final ConstraintViolation<?> violation;

        StoredViolationContext(ConstraintViolation<?> violation) {
            this.violation = violation;
        }

        @Override
        public ConstraintDescriptor<?> getConstraintDescriptor() {
            return violation.getConstraintDescriptor();
        }

        @Override
        public Object getValidatedValue() {
            return violation.getInvalidValue();
        }

        @Override
        public <T> T unwrap(Class<T> type) {
            if (type.isInstance(this)) {
                return type.cast(this);
            }
            throw new ValidationException("Cannot unwrap to " + type.getName());
        }
    }
}
