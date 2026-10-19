/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_validator.hibernate_validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.metadata.ConstraintDescriptor;
import jakarta.validation.metadata.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.constraints.CodePointLength;
import org.hibernate.validator.constraints.CreditCardNumber;
import org.hibernate.validator.constraints.EAN;
import org.hibernate.validator.constraints.ISBN;
import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.constraints.LuhnCheck;
import org.hibernate.validator.constraints.Mod10Check;
import org.hibernate.validator.constraints.Mod11Check;
import org.hibernate.validator.constraints.Normalized;
import org.hibernate.validator.constraints.Range;
import org.hibernate.validator.constraints.URL;
import org.hibernate.validator.constraints.UniqueElements;
import org.hibernate.validator.constraints.br.CNPJ;
import org.hibernate.validator.constraints.br.CPF;
import org.hibernate.validator.constraints.pl.NIP;
import org.hibernate.validator.constraints.pl.PESEL;
import org.hibernate.validator.constraints.pl.REGON;
import org.hibernate.validator.constraints.ru.INN;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Hibernate Validator's built-in identifier and check-digit constraints: `@ISBN`, `@EAN`,
 * `@Mod10Check`, `@Mod11Check`, `@LuhnCheck` and `@CreditCardNumber`, the national taxpayer
 * numbers `@CPF`, `@CNPJ`, `@NIP`, `@PESEL`, `@REGON` and `@INN`, and the text and collection
 * constraints `@URL`, `@Normalized`, `@CodePointLength`, `@Length`, `@UniqueElements` and `@Range`.
 *
 * A cross-border book wholesale marketplace onboards sellers from Brazil, Poland and Russia.
 * Each seller registers its tax identifiers, a payout card, a storefront and a catalog of
 * books identified by ISBN and EAN barcodes plus warehouse and supplier codes protected by
 * check digits. The onboarding service rejects mistyped identifiers before they reach the
 * tax and logistics back ends, and checks single form fields as the seller types them.
 */
public class BookWholesaleIdentifierConstraintsTest {

    private ValidatorFactory factory;
    private Validator validator;

    @BeforeEach
    void createValidator() {
        factory = Validation.byProvider(HibernateValidator.class)
                .configure()
                .defaultLocale(Locale.ENGLISH)
                .buildValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterEach
    void closeFactory() {
        factory.close();
    }

    @Test
    void acceptsCorrectlyTypedRegistration() {
        SellerRegistration registration = validRegistration();

        Set<ConstraintViolation<SellerRegistration>> violations = validator.validate(registration);

        assertTrue(violations.isEmpty(), () -> "unexpected violations: " + describe(violations));
    }

    @Test
    void acceptsAlternativeNotationsOfTheSameIdentifiers() {
        SellerRegistration registration = validRegistration();
        registration.ownerCpf = "11144477735";
        registration.companyCnpj = "11222333000181";
        registration.polishVatId = "1234563218";
        registration.payout.cardNumber = "5500000000000004";
        registration.payout.printedCardNumber = "5500-0000-0000-0004";
        registration.catalog.get(0).isbn13 = "9780306406157";
        registration.catalog.get(0).anyIsbn = "978-0-306-40615-7";

        Set<ConstraintViolation<SellerRegistration>> violations = validator.validate(registration);

        assertTrue(violations.isEmpty(), () -> "unexpected violations: " + describe(violations));
    }

    @Test
    void rejectsMistypedNationalTaxpayerNumbers() {
        SellerRegistration registration = validRegistration();
        registration.ownerCpf = "111.444.777-36";
        registration.companyCnpj = "11.222.333/0001-82";
        registration.polishVatId = "123-456-32-19";
        registration.polishOwnerPesel = "44051401358";
        registration.polishRegon = "123456786";
        registration.russianCompanyInn = "7707083894";

        Set<ConstraintViolation<SellerRegistration>> violations = validator.validate(registration);

        Map<String, String> expected = new TreeMap<>();
        expected.put("ownerCpf", "invalid Brazilian individual taxpayer registry number (CPF)");
        expected.put("companyCnpj", "invalid Brazilian corporate taxpayer registry number (CNPJ)");
        expected.put("polishVatId", "invalid VAT Identification Number (NIP)");
        expected.put("polishOwnerPesel", "invalid Polish National Identification Number (PESEL)");
        expected.put("polishRegon", "invalid Polish Taxpayer Identification Number (REGON)");
        expected.put("russianCompanyInn", "invalid Russian taxpayer identification number (INN)");
        assertEquals(expected, describe(violations));
    }

    @Test
    void enforcesTheInnLengthOfTheRegisteredTaxpayerType() {
        SellerRegistration registration = validRegistration();
        registration.russianCompanyInn = "500100732259";

        Set<ConstraintViolation<SellerRegistration>> violations = validator.validate(registration);

        assertEquals(Map.of("russianCompanyInn", INN.class), annotationsByPath(violations));
        assertTrue(validator.validateValue(SellerRegistration.class, "russianOwnerInn", "500100732259")
                .isEmpty());
        assertEquals(1, validator.validateValue(SellerRegistration.class, "russianOwnerInn", "7707083893")
                .size());
    }

    @Test
    void rejectsCatalogBarcodesWithWrongCheckDigits() {
        SellerRegistration registration = validRegistration();
        BookListing book = registration.catalog.get(1);
        book.isbn13 = "978-0-306-40615-8";
        book.anyIsbn = "0-306-40615-3";
        book.gtin = "4006381333932";
        book.shelfLabel = "96385075";
        book.warehouseCode = "12345671";
        book.supplierPartNumber = "123456";

        Set<ConstraintViolation<SellerRegistration>> violations = validator.validate(registration);

        Map<String, String> expected = new TreeMap<>();
        expected.put("catalog[1].isbn13", "invalid ISBN");
        expected.put("catalog[1].anyIsbn", "invalid ISBN");
        expected.put("catalog[1].gtin", "invalid EAN13 barcode");
        expected.put("catalog[1].shelfLabel", "invalid EAN8 barcode");
        expected.put("catalog[1].warehouseCode",
                "the check digit for 12345671 is invalid, Modulo 10 checksum failed");
        expected.put("catalog[1].supplierPartNumber",
                "the check digit for 123456 is invalid, Modulo 11 checksum failed");
        assertEquals(expected, describe(violations));
    }

    @Test
    void rejectsIsbnOfTheWrongEdition() {
        SellerRegistration registration = validRegistration();
        registration.catalog.get(0).isbn13 = "0-306-40615-2";

        Set<ConstraintViolation<SellerRegistration>> violations = validator.validate(registration);

        assertEquals(Map.of("catalog[0].isbn13", ISBN.class), annotationsByPath(violations));
    }

    @Test
    void rejectsPayoutCardAndLoyaltyNumbersFailingLuhn() {
        SellerRegistration registration = validRegistration();
        registration.payout.cardNumber = "4111 1111 1111 1111";
        registration.payout.printedCardNumber = "4111 1111 1111 1112";
        registration.payout.loyaltyCard = "7992-7398-714";

        Set<ConstraintViolation<SellerRegistration>> violations = validator.validate(registration);

        Map<String, String> expected = new TreeMap<>();
        expected.put("payout.cardNumber", "invalid credit card number");
        expected.put("payout.printedCardNumber", "invalid credit card number");
        expected.put("payout.loyaltyCard",
                "the check digit for 7992-7398-714 is invalid, Luhn Modulo 10 checksum failed");
        assertEquals(expected, describe(violations));
        assertEquals(CreditCardNumber.class, violations.stream()
                .filter(violation -> violation.getPropertyPath().toString().equals("payout.cardNumber"))
                .findFirst()
                .orElseThrow()
                .getConstraintDescriptor()
                .getAnnotation()
                .annotationType());
    }

    @Test
    void validatesStorefrontTextAndCollections() {
        SellerRegistration registration = validRegistration();
        String sixBooks = "\uD83D\uDCDA".repeat(6);
        registration.storefrontUrl = "http://books.example.com/stalls/42";
        registration.displayName = "Livraria Jose\u0301";
        registration.badge = sixBooks;
        registration.emojiTitle = sixBooks;
        registration.tags = List.of("poetry", "rare", "poetry");
        registration.minimumOrder = 750;

        Set<ConstraintViolation<SellerRegistration>> violations = validator.validate(registration);

        Map<String, String> expected = new TreeMap<>();
        expected.put("storefrontUrl", "must be a valid URL");
        expected.put("displayName", "must be normalized");
        expected.put("badge", "length must be between 0 and 10");
        expected.put("tags", "must only contain unique elements");
        expected.put("minimumOrder", "must be between 1 and 500");
        assertEquals(expected, describe(violations));
    }

    @Test
    void checksSingleFormFieldsAsTheSellerTypes() {
        assertTrue(validator.validateValue(SellerRegistration.class, "ownerCpf", "111.444.777-35").isEmpty());
        assertTrue(validator.validateValue(SellerRegistration.class, "ownerCpf", null).isEmpty());

        Set<ConstraintViolation<SellerRegistration>> typo =
                validator.validateValue(SellerRegistration.class, "ownerCpf", "111.444.777-53");
        assertEquals(1, typo.size());
        ConstraintViolation<SellerRegistration> violation = typo.iterator().next();
        assertEquals(CPF.class, violation.getConstraintDescriptor().getAnnotation().annotationType());
        assertEquals("111.444.777-53", violation.getInvalidValue());

        assertTrue(validator.validateValue(BookListing.class, "gtin", "4006381333931").isEmpty());
        assertEquals(1, validator.validateValue(BookListing.class, "gtin", "400638133393").size());
        assertTrue(validator.validateValue(SellerRegistration.class, "storefrontUrl",
                "https://books.example.com/stalls/42").isEmpty());
    }

    @Test
    @SuppressWarnings("checkstyle:annotationAccess")
    void exposesIdentifierConstraintMetadataToTheOnboardingForm() {
        PropertyDescriptor shelfLabel = validator.getConstraintsForClass(BookListing.class)
                .getConstraintsForProperty("shelfLabel");
        ConstraintDescriptor<?> ean = shelfLabel.getConstraintDescriptors().iterator().next();
        assertEquals(EAN.class, ean.getAnnotation().annotationType());
        assertEquals(EAN.Type.EAN8, ean.getAttributes().get("type"));

        PropertyDescriptor cardNumber = validator.getConstraintsForClass(PayoutProfile.class)
                .getConstraintsForProperty("cardNumber");
        ConstraintDescriptor<?> card = cardNumber.getConstraintDescriptors().iterator().next();
        assertTrue(card.isReportAsSingleViolation());
        assertEquals(Set.of(LuhnCheck.class), annotationTypes(card.getComposingConstraints()));

        PropertyDescriptor ownerCpf = validator.getConstraintsForClass(SellerRegistration.class)
                .getConstraintsForProperty("ownerCpf");
        ConstraintDescriptor<?> cpf = ownerCpf.getConstraintDescriptors().iterator().next();
        assertTrue(cpf.isReportAsSingleViolation());
        assertTrue(annotationTypes(cpf.getComposingConstraints()).contains(Pattern.class));

        PropertyDescriptor supplierPart = validator.getConstraintsForClass(BookListing.class)
                .getConstraintsForProperty("supplierPartNumber");
        Map<String, Object> mod11 = supplierPart.getConstraintDescriptors().iterator().next().getAttributes();
        assertEquals('X', mod11.get("treatCheck10As"));
        assertEquals(Mod11Check.ProcessingDirection.RIGHT_TO_LEFT, mod11.get("processingDirection"));
    }

    private static SellerRegistration validRegistration() {
        SellerRegistration registration = new SellerRegistration();
        registration.ownerCpf = "111.444.777-35";
        registration.companyCnpj = "11.222.333/0001-81";
        registration.polishVatId = "123-456-32-18";
        registration.polishOwnerPesel = "44051401359";
        registration.polishRegon = "123456785";
        registration.russianCompanyInn = "7707083893";
        registration.russianOwnerInn = "500100732259";
        registration.storefrontUrl = "https://books.example.com/stalls/42";
        registration.displayName = "Livraria Jos\u00e9";
        registration.badge = "rare";
        registration.emojiTitle = "\uD83D\uDCDA".repeat(6);
        registration.tags = List.of("poetry", "rare", "first-edition");
        registration.minimumOrder = 25;

        registration.payout = new PayoutProfile();
        registration.payout.cardNumber = "4111111111111111";
        registration.payout.printedCardNumber = "4111 1111 1111 1111";
        registration.payout.loyaltyCard = "7992-7398-713";

        registration.catalog = new ArrayList<>();
        registration.catalog.add(book("978-0-306-40615-7", "0-306-40615-2"));
        registration.catalog.add(book("978-3-16-148410-0", "978-3-16-148410-0"));
        return registration;
    }

    private static BookListing book(String isbn13, String anyIsbn) {
        BookListing book = new BookListing();
        book.isbn13 = isbn13;
        book.anyIsbn = anyIsbn;
        book.gtin = "4006381333931";
        book.shelfLabel = "96385074";
        book.warehouseCode = "12345670";
        book.supplierPartNumber = "123455";
        return book;
    }

    private static <T> Map<String, String> describe(Set<ConstraintViolation<T>> violations) {
        return violations.stream().collect(Collectors.toMap(
                violation -> violation.getPropertyPath().toString(),
                ConstraintViolation::getMessage,
                (first, second) -> first + " | " + second,
                TreeMap::new));
    }

    private static <T> Map<String, Class<? extends Annotation>> annotationsByPath(
            Set<ConstraintViolation<T>> violations) {
        return violations.stream().collect(Collectors.toMap(
                violation -> violation.getPropertyPath().toString(),
                violation -> violation.getConstraintDescriptor().getAnnotation().annotationType()));
    }

    @SuppressWarnings("checkstyle:annotationAccess")
    private static Set<Class<? extends Annotation>> annotationTypes(Set<ConstraintDescriptor<?>> descriptors) {
        return descriptors.stream()
                .map(descriptor -> descriptor.getAnnotation().annotationType())
                .collect(Collectors.toSet());
    }

    /** A seller's onboarding form, with tax identifiers from each supported country. */
    public static class SellerRegistration {

        @CPF
        String ownerCpf;

        @CNPJ
        String companyCnpj;

        @NIP
        String polishVatId;

        @PESEL
        String polishOwnerPesel;

        @REGON
        String polishRegon;

        @INN(type = INN.Type.JURIDICAL)
        String russianCompanyInn;

        @INN(type = INN.Type.INDIVIDUAL)
        String russianOwnerInn;

        @URL(protocol = "https")
        String storefrontUrl;

        @Normalized(form = Normalizer.Form.NFC)
        String displayName;

        @Length(max = 10)
        String badge;

        @CodePointLength(max = 10)
        String emojiTitle;

        @UniqueElements
        List<String> tags;

        @Range(min = 1, max = 500)
        int minimumOrder;

        @NotNull
        @Valid
        PayoutProfile payout;

        @NotNull
        List<@Valid BookListing> catalog;
    }

    /** The card the marketplace pays the seller to, and the seller's loyalty programme card. */
    public static class PayoutProfile {

        @CreditCardNumber
        String cardNumber;

        @CreditCardNumber(ignoreNonDigitCharacters = true)
        String printedCardNumber;

        @LuhnCheck(ignoreNonDigitCharacters = true)
        String loyaltyCard;
    }

    /** One book in the seller's catalog with its barcodes and internal check-digit codes. */
    public static class BookListing {

        @ISBN(type = ISBN.Type.ISBN_13)
        String isbn13;

        @ISBN(type = ISBN.Type.ANY)
        String anyIsbn;

        @EAN
        String gtin;

        @EAN(type = EAN.Type.EAN8)
        String shelfLabel;

        @Mod10Check(multiplier = 3, weight = 1)
        String warehouseCode;

        @Mod11Check
        String supplierPartNumber;
    }
}
