/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.CSVReader;
import com.opencsv.bean.CsvCustomBindByName;
import com.opencsv.bean.HeaderColumnNameMappingStrategy;
import com.opencsv.bean.customconverter.ConvertGermanToBoolean;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(120)
public class ConverterLanguageToBooleanTest {
    @Test
    void readsAndWritesGermanBooleanValuesAndRejectsUnknownInput() throws Exception {
        HeaderColumnNameMappingStrategy<Flag> strategy = new HeaderColumnNameMappingStrategy<>();
        strategy.setErrorLocale(Locale.US);
        strategy.setType(Flag.class);
        try (CSVReader reader = new CSVReader(new StringReader("enabled\n"))) {
            strategy.captureHeader(reader);
        }
        Flag bean = strategy.populateNewBean(new String[] {"ja"});
        assertThat(bean.enabled).isTrue();
        assertThat(strategy.transmuteBean(bean)).containsExactly("wahr");
        bean.enabled = false;
        assertThat(strategy.transmuteBean(bean)).containsExactly("falsch");
        assertThatThrownBy(() -> strategy.populateNewBean(new String[] {"perhaps"}))
                .isInstanceOf(CsvDataTypeMismatchException.class)
                .hasMessageContaining("not a boolean value");
    }

    @Test
    void rejectsNonBooleanFieldsWhenWritingLocalizedValues() throws Exception {
        HeaderColumnNameMappingStrategy<TextFlag> strategy = new HeaderColumnNameMappingStrategy<>();
        strategy.setErrorLocale(Locale.US);
        strategy.setType(TextFlag.class);
        TextFlag bean = new TextFlag();
        bean.enabled = "ja";
        strategy.generateHeader(bean);
        assertThatThrownBy(() -> strategy.transmuteBean(bean))
                .isInstanceOf(CsvDataTypeMismatchException.class)
                .hasMessageContaining("Boolean or boolean");
    }

    public static class Flag {
        @CsvCustomBindByName(converter = ConvertGermanToBoolean.class)
        public Boolean enabled;
    }

    public static class TextFlag {
        @CsvCustomBindByName(converter = ConvertGermanToBoolean.class)
        public String enabled;
    }
}
