/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_jopt_simple.jopt_simple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.nio.file.Path;

import joptsimple.ValueConversionException;
import joptsimple.util.PathConverter;
import joptsimple.util.PathProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class PathConverterTest {
    @TempDir
    Path tempDir;

    @Test
    void formatsMissingFileMessageFromTheResourceBundle() {
        Path missingFile = tempDir.resolve("missing-option-path");
        PathConverter converter = new PathConverter(PathProperties.FILE_EXISTING);

        ValueConversionException exception = catchThrowableOfType(
                () -> converter.convert(missingFile.toString()),
                ValueConversionException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getMessage())
                .contains(missingFile.toString())
                .contains("does not exist");
    }
}
