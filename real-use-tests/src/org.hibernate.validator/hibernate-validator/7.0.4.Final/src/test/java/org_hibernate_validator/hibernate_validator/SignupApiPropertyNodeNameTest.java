/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_validator.hibernate_validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Path;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.spi.nodenameprovider.JavaBeanProperty;
import org.hibernate.validator.spi.nodenameprovider.Property;
import org.hibernate.validator.spi.nodenameprovider.PropertyNodeNameProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Property path naming through a `PropertyNodeNameProvider`: an application provider that
 * resolves node names from Jackson's view of a bean (`@JsonProperty` renames and
 * `@JsonNaming` strategies) on field, getter, cascaded and list element properties, its use by
 * `validateProperty` and `validateValue`, and the default provider that keeps Java property
 * names.
 *
 * A signup endpoint deserializes a JSON request with Jackson, validates it, and answers with
 * an error document keyed by the same JSON paths the client sent, so a front end can attach
 * each message to the form field it came from.
 */
public class SignupApiPropertyNodeNameTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ValidatorFactory factory;
    private Validator validator;

    @BeforeEach
    void createValidator() {
        factory = Validation.byProvider(HibernateValidator.class)
                .configure()
                .defaultLocale(Locale.ENGLISH)
                .propertyNodeNameProvider(new JacksonPropertyNodeNameProvider())
                .buildValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterEach
    void closeFactory() {
        factory.close();
    }

    @Test
    void validRequestDeserializedFromJsonHasNoViolations() throws Exception {
        SignupRequest request = objectMapper.readValue("""
                {
                  "first_name": "Ada",
                  "email_address": "ada@example.com",
                  "password": "correct-horse",
                  "billing": {"street_line": "12 Analytical Way", "postal_code": "10115"},
                  "seats": [{"seat_name": "Ada", "monthly_quota": 40}]
                }
                """, SignupRequest.class);

        assertEquals("10115", request.getBilling().getPostalCode());
        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void errorDocumentUsesJsonPathsForEveryKindOfProperty() throws Exception {
        SignupRequest request = objectMapper.readValue("""
                {
                  "first_name": "",
                  "email_address": "not-an-email",
                  "password": "short",
                  "billing": {"street_line": "12 Analytical Way", "postal_code": "ABC"},
                  "seats": [
                    {"seat_name": "Ada", "monthly_quota": 40},
                    {"seat_name": "Charles", "monthly_quota": 0}
                  ]
                }
                """, SignupRequest.class);

        Map<String, String> errors = errorDocument(validator.validate(request));

        assertEquals(Map.of(
                "first_name", "must not be blank",
                "email_address", "must be a well-formed email address",
                "password", "size must be between 8 and 64",
                "billing.postal_code", "postal code must have five digits",
                "seats[1].monthly_quota", "must be greater than 0"), errors);
    }

    @Test
    void getterConstraintIsReportedUnderItsJsonName() throws Exception {
        SignupRequest request = objectMapper.readValue("""
                {
                  "first_name": "Ada",
                  "email_address": "ada@example.com",
                  "password": "correct-horse",
                  "billing": {"street_line": "12 Analytical Way", "postal_code": "10115"},
                  "seats": [
                    {"seat_name": "Ada", "monthly_quota": 40},
                    {"seat_name": "Charles", "monthly_quota": 30}
                  ]
                }
                """, SignupRequest.class);
        request.setPlan("starter");

        Set<ConstraintViolation<SignupRequest>> violations = validator.validate(request);

        assertEquals(Map.of("plan_fits_seats", "starter plan allows one seat"), errorDocument(violations));
        ConstraintViolation<SignupRequest> violation = violations.iterator().next();
        assertEquals(false, violation.getInvalidValue());
        Path.Node node = violation.getPropertyPath().iterator().next();
        assertEquals("plan_fits_seats", node.getName());
    }

    @Test
    void cascadedNodesCarryJsonNamesAndListIndexes() throws Exception {
        SignupRequest request = objectMapper.readValue("""
                {
                  "first_name": "Ada",
                  "email_address": "ada@example.com",
                  "password": "correct-horse",
                  "billing": {"street_line": "12 Analytical Way", "postal_code": "10115"},
                  "seats": [
                    {"seat_name": "Ada", "monthly_quota": 40},
                    {"seat_name": "", "monthly_quota": 40}
                  ]
                }
                """, SignupRequest.class);

        Set<ConstraintViolation<SignupRequest>> violations = validator.validate(request);

        assertEquals(1, violations.size());
        List<Path.Node> nodes = new ArrayList<>();
        violations.iterator().next().getPropertyPath().forEach(nodes::add);
        assertEquals(2, nodes.size());
        assertEquals("seats", nodes.get(0).getName());
        assertEquals("seat_name", nodes.get(1).getName());
        assertTrue(nodes.get(1).isInIterable());
        assertEquals(1, nodes.get(1).getIndex());
    }

    @Test
    void singleFieldChecksLookUpJavaNamesButReportJsonNames() {
        BillingAddress address = new BillingAddress();
        address.setStreetLine("12 Analytical Way");
        address.setPostalCode("1011");

        Set<ConstraintViolation<BillingAddress>> byProperty =
                validator.validateProperty(address, "postalCode");
        Set<ConstraintViolation<BillingAddress>> byValue =
                validator.validateValue(BillingAddress.class, "streetLine", " ");

        assertEquals(Map.of("postal_code", "postal code must have five digits"), errorDocument(byProperty));
        assertEquals(Map.of("street_line", "must not be blank"), errorDocument(byValue));
    }

    @Test
    void defaultProviderKeepsJavaPropertyNames() throws Exception {
        SignupRequest request = objectMapper.readValue("""
                {
                  "first_name": "Ada",
                  "email_address": "ada@example.com",
                  "password": "correct-horse",
                  "billing": {"street_line": "12 Analytical Way", "postal_code": "ABC"},
                  "seats": [{"seat_name": "Ada", "monthly_quota": -1}]
                }
                """, SignupRequest.class);

        try (ValidatorFactory plain = Validation.byProvider(HibernateValidator.class)
                .configure()
                .defaultLocale(Locale.ENGLISH)
                .buildValidatorFactory()) {
            Map<String, String> errors = errorDocument(plain.getValidator().validate(request));

            assertEquals(Map.of(
                    "billing.postalCode", "postal code must have five digits",
                    "seats[0].monthlyQuota", "must be greater than 0"), errors);
        }
    }

    /// Builds the error document the endpoint returns: one message per JSON path.
    private static <T> Map<String, String> errorDocument(Set<ConstraintViolation<T>> violations) {
        Map<String, String> errors = new TreeMap<>();
        for (ConstraintViolation<T> violation : violations) {
            errors.put(violation.getPropertyPath().toString(), violation.getMessage());
        }
        return errors;
    }

    /// Names property nodes the way Jackson serializes the declaring bean.
    public static class JacksonPropertyNodeNameProvider implements PropertyNodeNameProvider {

        private final ObjectMapper mapper = new ObjectMapper();

        @Override
        public String getName(Property property) {
            if (property instanceof JavaBeanProperty beanProperty) {
                JavaType type = mapper.constructType(beanProperty.getDeclaringClass());
                BeanDescription description = mapper.getSerializationConfig().introspect(type);
                return description.findProperties()
                        .stream()
                        .filter(candidate -> candidate.getInternalName().equals(property.getName()))
                        .map(BeanPropertyDefinition::getName)
                        .findFirst()
                        .orElse(property.getName());
            }
            return property.getName();
        }
    }

    /// The JSON body of `POST /signup`.
    public static class SignupRequest {

        @NotBlank
        @JsonProperty("first_name")
        private String firstName;

        @NotNull
        @Email
        @JsonProperty("email_address")
        private String email;

        @NotNull
        @Size(min = 8, max = 64)
        private String password;

        @NotNull
        @Valid
        private BillingAddress billing;

        @NotEmpty
        private List<@Valid Seat> seats = new ArrayList<>();

        private String plan = "team";

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public BillingAddress getBilling() {
            return billing;
        }

        public void setBilling(BillingAddress billing) {
            this.billing = billing;
        }

        public List<Seat> getSeats() {
            return seats;
        }

        public void setSeats(List<Seat> seats) {
            this.seats = seats;
        }

        public String getPlan() {
            return plan;
        }

        public void setPlan(String plan) {
            this.plan = plan;
        }

        /// Derived flag the API also returns; only the team plan may hold several seats.
        @JsonProperty("plan_fits_seats")
        @AssertTrue(message = "starter plan allows one seat")
        public boolean isPlanFitsSeats() {
            return !"starter".equals(plan) || seats.size() <= 1;
        }
    }

    /// Billing address, named in snake case by a Jackson naming strategy.
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class BillingAddress {

        @NotBlank
        private String streetLine;

        @NotNull
        @Pattern(regexp = "\\d{5}", message = "postal code must have five digits")
        private String postalCode;

        public String getStreetLine() {
            return streetLine;
        }

        public void setStreetLine(String streetLine) {
            this.streetLine = streetLine;
        }

        public String getPostalCode() {
            return postalCode;
        }

        public void setPostalCode(String postalCode) {
            this.postalCode = postalCode;
        }
    }

    /// One licensed seat of the account.
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Seat {

        @NotBlank
        private String seatName;

        @Positive
        private int monthlyQuota;

        public String getSeatName() {
            return seatName;
        }

        public void setSeatName(String seatName) {
            this.seatName = seatName;
        }

        public int getMonthlyQuota() {
            return monthlyQuota;
        }

        public void setMonthlyQuota(int monthlyQuota) {
            this.monthlyQuota = monthlyQuota;
        }
    }
}
