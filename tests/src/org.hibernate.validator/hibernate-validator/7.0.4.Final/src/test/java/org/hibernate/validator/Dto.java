/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.hibernate.validator;

import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Negative;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.CreditCardNumber;
import org.hibernate.validator.constraints.Range;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class Dto {
    @NotBlank
    private final String notBlank;

    @NotEmpty
    private final List<String> notEmpty;

    @Min(1)
    private final int min;

    @Max(-1)
    private final int max;

    @Email
    private final String email;

    @Pattern(regexp = "[a-z]")
    private final String pattern;

    @NotNull
    private final Object notNull;

    @Null
    private final Object oNull;

    @Future
    private final Instant future;

    @AssertTrue
    private final boolean bTrue;

    @AssertFalse
    private final boolean bFalse;

    @Digits(integer = 2, fraction = 0)
    private final int digits;

    @NotEmpty
    private final String notEmptyString;

    @NotEmpty
    private final Map<String, String> notEmptyMap;

    @NotEmpty
    private final String[] notEmptyArray;

    @Size(min = 1, max = 2)
    private final String size;

    @Positive
    private final int positive;

    @Negative
    private final int negative;

    @CreditCardNumber
    private final String ccNumber;

    @Range(min = 1, max = 10)
    private final int range;

    public Dto(
            String notBlank, List<String> notEmpty, int min, int max, String email, String pattern, Object notNull,
            Object oNull, Instant future, boolean bTrue, boolean bFalse, int digits, String notEmptyString,
            Map<String, String> notEmptyMap, @NotEmpty String[] notEmptyArray, String size, int positive, int negative,
            String ccNumber, int range
    ) {
        this.notBlank = notBlank;
        this.notEmpty = notEmpty;
        this.min = min;
        this.max = max;
        this.email = email;
        this.pattern = pattern;
        this.notNull = notNull;
        this.oNull = oNull;
        this.future = future;
        this.bTrue = bTrue;
        this.bFalse = bFalse;
        this.digits = digits;
        this.notEmptyString = notEmptyString;
        this.notEmptyMap = notEmptyMap;
        this.notEmptyArray = notEmptyArray;
        this.size = size;
        this.positive = positive;
        this.negative = negative;
        this.ccNumber = ccNumber;
        this.range = range;
    }

    public static Dto createValid() {
        return new Dto(
                "not-blank", List.of("not-empty"), 2, -2, "some@example.com", "a",
                new Object(), null, Instant.now().plusSeconds(60), true, false, 11,
                "some-content", Map.of("a", "1"), new String[]{"some-string"}, "12", 1,
                -2, "4539627380966582", 7
        );
    }

    public static Dto createInvalid() {
        return new Dto(
                "", List.of(), 0, 0, "no-email", "1", null, new Object(),
                Instant.now().minusSeconds(60), false, true, 111, "",
                Map.of(), new String[0], "123", -1, 1, "", 11
        );
    }
}
