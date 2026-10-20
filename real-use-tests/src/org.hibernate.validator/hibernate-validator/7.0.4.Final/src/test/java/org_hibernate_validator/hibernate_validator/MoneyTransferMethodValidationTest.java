/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_validator.hibernate_validator;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.ConstraintDeclarationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.executable.ExecutableValidator;
import jakarta.validation.metadata.ConstructorDescriptor;
import jakarta.validation.metadata.MethodDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.hibernate.validator.HibernateValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Method validation through the `ExecutableValidator`: parameter and return value constraints
 * declared on a service interface and inherited by its implementation, constructor parameter
 * constraints, a cascaded return value, executable metadata, and the rule that an overriding
 * method must not redefine its parameter constraints.
 *
 * A payment backend guards its transfer service the way a method interceptor does: before a
 * call it validates the arguments, after the call it validates the returned receipt, and it
 * validates the arguments of the service constructor before wiring the service. The backend
 * reports each violation by the parameter index or return value its property path names.
 */
public class MoneyTransferMethodValidationTest {

    private static final String SOURCE_IBAN = "DE89370400440532013000";
    private static final String TARGET_IBAN = "GB29NWBK60161331926819";

    private ValidatorFactory factory;
    private ExecutableValidator executableValidator;

    @BeforeEach
    void createValidator() {
        factory = Validation.byProvider(HibernateValidator.class)
                .configure()
                .defaultLocale(Locale.ENGLISH)
                .buildValidatorFactory();
        executableValidator = factory.getValidator().forExecutables();
    }

    @AfterEach
    void closeFactory() {
        factory.close();
    }

    @Test
    void acceptsValidCallAndReceipt() throws NoSuchMethodException {
        LedgerTransferService service = new LedgerTransferService("BANKDE", 100_000);
        Object[] arguments = {SOURCE_IBAN, TARGET_IBAN, new BigDecimal("250.00")};

        Set<ConstraintViolation<LedgerTransferService>> parameterViolations =
                executableValidator.validateParameters(service, transferMethod(), arguments);
        Receipt receipt = service.transfer(SOURCE_IBAN, TARGET_IBAN, new BigDecimal("250.00"));
        Set<ConstraintViolation<LedgerTransferService>> returnViolations =
                executableValidator.validateReturnValue(service, transferMethod(), receipt);

        assertTrue(parameterViolations.isEmpty(), () -> "unexpected violations: " + describe(parameterViolations));
        assertTrue(returnViolations.isEmpty(), () -> "unexpected violations: " + describe(returnViolations));
        assertEquals("BANKDE-1", receipt.reference);
        assertEquals(new BigDecimal("2.50"), receipt.fee);
    }

    @Test
    void reportsInheritedParameterConstraintsByIndex() throws NoSuchMethodException {
        LedgerTransferService service = new LedgerTransferService("BANKDE", 100_000);
        Method transfer = transferMethod();
        Object[] arguments = {null, "not an iban", new BigDecimal("-5.00")};

        Set<ConstraintViolation<LedgerTransferService>> violations =
                executableValidator.validateParameters(service, transfer, arguments);

        assertEquals(List.of(
                "0 NotNull must not be null",
                "1 Pattern must match \"[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}\"",
                "2 DecimalMin must be greater than or equal to 0.01"), describe(violations));
        ConstraintViolation<LedgerTransferService> amountViolation = violationAtParameter(violations, 2);
        assertSame(service, amountViolation.getRootBean());
        assertSame(service, amountViolation.getLeafBean());
        assertEquals(LedgerTransferService.class, amountViolation.getRootBeanClass());
        assertEquals(new BigDecimal("-5.00"), amountViolation.getInvalidValue());
        assertArrayEquals(arguments, amountViolation.getExecutableParameters());
        assertNull(amountViolation.getExecutableReturnValue());

        Iterator<Path.Node> nodes = amountViolation.getPropertyPath().iterator();
        Path.Node methodNode = nodes.next();
        assertEquals(ElementKind.METHOD, methodNode.getKind());
        assertEquals("transfer", methodNode.getName());
        assertEquals(
                List.of(String.class, String.class, BigDecimal.class),
                methodNode.as(Path.MethodNode.class).getParameterTypes());
        Path.Node parameterNode = nodes.next();
        assertEquals(ElementKind.PARAMETER, parameterNode.getKind());
        assertEquals(2, parameterNode.as(Path.ParameterNode.class).getParameterIndex());
        assertFalse(nodes.hasNext());
    }

    @Test
    void reportsAmountWithTooManyFractionDigits() throws NoSuchMethodException {
        LedgerTransferService service = new LedgerTransferService("BANKDE", 100_000);

        Set<ConstraintViolation<LedgerTransferService>> violations = executableValidator.validateParameters(
                service, transferMethod(), new Object[] {SOURCE_IBAN, TARGET_IBAN, new BigDecimal("12.345")});

        assertEquals(List.of("2 Digits numeric value out of bounds (<9 digits>.<2 digits> expected)"),
                describe(violations));
    }

    @Test
    void cascadesIntoReturnedReceipt() throws NoSuchMethodException {
        LedgerTransferService service = new LedgerTransferService("BANKDE", 100_000);
        Receipt rejected = service.transfer(SOURCE_IBAN, TARGET_IBAN, new BigDecimal("1500.00"));

        Set<ConstraintViolation<LedgerTransferService>> violations =
                executableValidator.validateReturnValue(service, transferMethod(), rejected);

        assertEquals(1, violations.size(), () -> "unexpected violations: " + describe(violations));
        ConstraintViolation<LedgerTransferService> violation = violations.iterator().next();
        assertEquals("must not be blank", violation.getMessage());
        assertSame(rejected, violation.getExecutableReturnValue());
        assertSame(rejected, violation.getLeafBean());
        assertSame(service, violation.getRootBean());
        assertNull(violation.getExecutableParameters());

        List<Path.Node> nodes = new ArrayList<>();
        violation.getPropertyPath().forEach(nodes::add);
        assertEquals(3, nodes.size());
        assertEquals(ElementKind.METHOD, nodes.get(0).getKind());
        assertEquals(ElementKind.RETURN_VALUE, nodes.get(1).getKind());
        assertEquals("<return value>", nodes.get(1).getName());
        assertEquals(ElementKind.PROPERTY, nodes.get(2).getKind());
        assertEquals("reference", nodes.get(2).getName());
        assertEquals("transfer.<return value>.reference", violation.getPropertyPath().toString());
    }

    @Test
    void rejectsMissingReceipt() throws NoSuchMethodException {
        LedgerTransferService service = new LedgerTransferService("BANKDE", 100_000);

        Set<ConstraintViolation<LedgerTransferService>> violations =
                executableValidator.validateReturnValue(service, transferMethod(), null);

        assertEquals(1, violations.size());
        ConstraintViolation<LedgerTransferService> violation = violations.iterator().next();
        assertEquals("must not be null", violation.getMessage());
        assertEquals("transfer.<return value>", violation.getPropertyPath().toString());
    }

    @Test
    void validatesConstructorParametersBeforeWiring() throws NoSuchMethodException {
        Constructor<LedgerTransferService> constructor =
                LedgerTransferService.class.getConstructor(String.class, int.class);

        Set<ConstraintViolation<LedgerTransferService>> valid =
                executableValidator.validateConstructorParameters(constructor, new Object[] {"BANKDE", 0});
        Set<ConstraintViolation<LedgerTransferService>> violations =
                executableValidator.validateConstructorParameters(constructor, new Object[] {" ", -1});

        assertTrue(valid.isEmpty(), () -> "unexpected violations: " + describe(valid));
        assertEquals(List.of(
                "0 NotBlank must not be blank",
                "1 Min must be greater than or equal to 0"), describe(violations));
        ConstraintViolation<LedgerTransferService> violation = violationAtParameter(violations, 1);
        assertNull(violation.getRootBean());
        assertEquals(LedgerTransferService.class, violation.getRootBeanClass());
        Path.Node constructorNode = violation.getPropertyPath().iterator().next();
        assertEquals(ElementKind.CONSTRUCTOR, constructorNode.getKind());
        assertEquals("LedgerTransferService", constructorNode.getName());
    }

    @Test
    void describesConstrainedExecutables() {
        MethodDescriptor transfer = factory.getValidator()
                .getConstraintsForClass(LedgerTransferService.class)
                .getConstraintsForMethod("transfer", String.class, String.class, BigDecimal.class);
        ConstructorDescriptor constructor = factory.getValidator()
                .getConstraintsForClass(LedgerTransferService.class)
                .getConstraintsForConstructor(String.class, int.class);

        assertTrue(transfer.hasConstrainedParameters());
        assertTrue(transfer.hasConstrainedReturnValue());
        assertTrue(transfer.getReturnValueDescriptor().isCascaded());
        assertEquals(3, transfer.getParameterDescriptors().size());
        assertEquals(3, transfer.getParameterDescriptors().get(2).getConstraintDescriptors().size());
        assertTrue(constructor.hasConstrainedParameters());
        assertFalse(constructor.hasConstrainedReturnValue());
        assertNull(factory.getValidator()
                .getConstraintsForClass(LedgerTransferService.class)
                .getConstraintsForMethod("fee", BigDecimal.class));
    }

    @Test
    void rejectsOverrideThatRedefinesParameterConstraints() throws NoSuchMethodException {
        StrictTransferService service = new StrictTransferService();
        Method transfer = StrictTransferService.class.getMethod(
                "transfer", String.class, String.class, BigDecimal.class);
        Object[] arguments = {SOURCE_IBAN, TARGET_IBAN, BigDecimal.ONE};

        ConstraintDeclarationException exception = assertThrows(ConstraintDeclarationException.class,
                () -> executableValidator.validateParameters(service, transfer, arguments));

        assertTrue(exception.getMessage().startsWith("HV000151"), exception.getMessage());
    }

    private static Method transferMethod() throws NoSuchMethodException {
        return LedgerTransferService.class.getMethod("transfer", String.class, String.class, BigDecimal.class);
    }

    private static <T> ConstraintViolation<T> violationAtParameter(Set<ConstraintViolation<T>> violations, int index) {
        for (ConstraintViolation<T> violation : violations) {
            if (parameterIndex(violation) == index) {
                return violation;
            }
        }
        throw new AssertionError("no violation at parameter " + index + ": " + describe(violations));
    }

    private static int parameterIndex(ConstraintViolation<?> violation) {
        for (Path.Node node : violation.getPropertyPath()) {
            if (node.getKind() == ElementKind.PARAMETER) {
                return node.as(Path.ParameterNode.class).getParameterIndex();
            }
        }
        return -1;
    }

    /// Renders each violation as `<parameter index> <constraint> <message>`, sorted by index.
    private static List<String> describe(Set<? extends ConstraintViolation<?>> violations) {
        List<String> lines = new ArrayList<>();
        for (ConstraintViolation<?> violation : violations) {
            lines.add(parameterIndex(violation) + " "
                    + violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName() + " "
                    + violation.getMessage());
        }
        lines.sort(null);
        return lines;
    }

    /// The service contract: implementations inherit its parameter and return value constraints.
    public interface TransferService {

        @NotNull
        @Valid
        Receipt transfer(
                @NotNull @Pattern(regexp = "[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}") String fromIban,
                @NotNull @Pattern(regexp = "[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}") String toIban,
                @NotNull @DecimalMin("0.01") @Digits(integer = 9, fraction = 2) BigDecimal amount);
    }

    /// Books transfers up to a daily limit; a transfer over the limit yields a receipt without a reference.
    public static class LedgerTransferService implements TransferService {

        private final String bankCode;
        private final int dailyLimitCents;
        private int bookedTransfers;

        public LedgerTransferService(@NotBlank String bankCode, @Min(0) int dailyLimitCents) {
            this.bankCode = bankCode;
            this.dailyLimitCents = dailyLimitCents;
        }

        @Override
        public Receipt transfer(String fromIban, String toIban, BigDecimal amount) {
            if (amount.movePointRight(2).intValueExact() > dailyLimitCents) {
                return new Receipt("", fee(amount));
            }
            bookedTransfers++;
            return new Receipt(bankCode + "-" + bookedTransfers, fee(amount));
        }

        public BigDecimal fee(BigDecimal amount) {
            return amount.movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
        }
    }

    /// Tightens an inherited parameter, which method validation forbids.
    public static class StrictTransferService implements TransferService {

        @Override
        public Receipt transfer(String fromIban, String toIban, @DecimalMax("1000.00") BigDecimal amount) {
            return new Receipt("STRICT-1", BigDecimal.ZERO);
        }
    }

    /// The booking confirmation a transfer returns.
    public static class Receipt {

        @NotBlank
        final String reference;

        @NotNull
        @PositiveOrZero
        final BigDecimal fee;

        Receipt(String reference, BigDecimal fee) {
            this.reference = reference;
            this.fee = fee;
        }
    }
}
