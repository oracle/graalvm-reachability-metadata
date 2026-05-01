/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_jopt_simple.jopt_simple;

import joptsimple.ValueConversionException;
import joptsimple.util.DateConverter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JoptsimpleInternalMessagesTest {
    @Test
    public void formatsConverterFailureMessageFromResourceBundle() {
        DateConverter converter = DateConverter.datePattern( "yyyy-MM-dd" );

        assertThatThrownBy( () -> converter.convert( "not-a-date" ) )
            .isInstanceOf( ValueConversionException.class )
            .hasMessage( "Value [not-a-date] does not match date/time pattern [yyyy-MM-dd]" );
    }
}
