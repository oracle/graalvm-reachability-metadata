/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_univocity.univocity_parsers;

import com.univocity.parsers.conversions.EnumConversion;
import com.univocity.parsers.conversions.EnumSelector;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EnumConversionTest {
    @Test
    void convertsUsingCustomEnumField() {
        EnumConversion<FieldMappedStatus> conversion = new EnumConversion<>(
                FieldMappedStatus.class,
                null,
                null,
                "code",
                EnumSelector.CUSTOM_FIELD);

        assertThat(conversion.execute("A")).isEqualTo(FieldMappedStatus.ACTIVE);
        assertThat(conversion.execute("I")).isEqualTo(FieldMappedStatus.INACTIVE);
        assertThat(conversion.revert(FieldMappedStatus.ACTIVE)).isEqualTo("A");
    }

    @Test
    void convertsUsingCustomEnumMethod() {
        EnumConversion<MethodMappedPriority> conversion = new EnumConversion<>(
                MethodMappedPriority.class,
                null,
                null,
                "label",
                EnumSelector.CUSTOM_METHOD);

        assertThat(conversion.execute("high priority")).isEqualTo(MethodMappedPriority.HIGH);
        assertThat(conversion.execute("low priority")).isEqualTo(MethodMappedPriority.LOW);
        assertThat(conversion.revert(MethodMappedPriority.HIGH)).isEqualTo("high priority");
    }

    @Test
    void convertsUsingStaticFactoryMethod() {
        EnumConversion<FactoryMappedChannel> conversion = new EnumConversion<>(
                FactoryMappedChannel.class,
                null,
                null,
                "fromExternalName",
                EnumSelector.CUSTOM_METHOD);

        assertThat(conversion.execute("in-store")).isEqualTo(FactoryMappedChannel.STORE);
        assertThat(conversion.execute("online")).isEqualTo(FactoryMappedChannel.WEB);
    }

    public enum FieldMappedStatus {
        ACTIVE("A"),
        INACTIVE("I");

        public final String code;

        FieldMappedStatus(String code) {
            this.code = code;
        }
    }

    public enum MethodMappedPriority {
        HIGH("high priority"),
        LOW("low priority");

        private final String displayLabel;

        MethodMappedPriority(String displayLabel) {
            this.displayLabel = displayLabel;
        }

        public String label() {
            return displayLabel;
        }
    }

    public enum FactoryMappedChannel {
        WEB("online"),
        STORE("in-store");

        private final String externalName;

        FactoryMappedChannel(String externalName) {
            this.externalName = externalName;
        }

        public static FactoryMappedChannel fromExternalName(String externalName) {
            for (FactoryMappedChannel value : values()) {
                if (value.externalName.equals(externalName)) {
                    return value;
                }
            }
            throw new IllegalArgumentException("Unknown channel: " + externalName);
        }
    }
}
