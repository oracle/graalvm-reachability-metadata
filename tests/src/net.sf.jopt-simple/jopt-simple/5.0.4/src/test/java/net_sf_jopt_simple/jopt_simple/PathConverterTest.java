/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_jopt_simple.jopt_simple;

import java.nio.file.Path;

import joptsimple.ValueConversionException;
import joptsimple.util.PathConverter;
import joptsimple.util.PathProperties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PathConverterTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    public void returnsPathWhenRequiredDirectoryExists() {
        PathConverter converter = new PathConverter( PathProperties.DIRECTORY_EXISTING );

        Path converted = converter.convert( temporaryDirectory.toString() );

        assertThat( converted ).isEqualTo( temporaryDirectory );
    }

    @Test
    public void formatsMissingFileMessageFromResourceBundle() {
        Path missingFile = temporaryDirectory.resolve( "missing-file" );
        PathConverter converter = new PathConverter( PathProperties.FILE_EXISTING );

        assertThatThrownBy( () -> converter.convert( missingFile.toString() ) )
            .isInstanceOf( ValueConversionException.class )
            .hasMessage( "File [" + missingFile + "] does not exist" );
    }
}
