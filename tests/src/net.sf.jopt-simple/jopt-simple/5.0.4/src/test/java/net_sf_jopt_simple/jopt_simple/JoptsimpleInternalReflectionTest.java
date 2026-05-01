/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_jopt_simple.jopt_simple;

import java.io.File;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JoptsimpleInternalReflectionTest {
    @Test
    public void convertsOptionWithStaticValueOfFactory() {
        OptionParser parser = new OptionParser();
        OptionSpec<Integer> count = parser.accepts( "count" ).withRequiredArg().ofType( Integer.class );

        OptionSet options = parser.parse( "--count", "37" );

        assertThat( options.valueOf( count ) ).isEqualTo( 37 );
    }

    @Test
    public void convertsOptionWithStringConstructor() {
        OptionParser parser = new OptionParser();
        OptionSpec<File> output = parser.accepts( "output" ).withRequiredArg().ofType( File.class );

        OptionSet options = parser.parse( "--output", "reports/result.txt" );

        assertThat( options.valueOf( output ) ).isEqualTo( new File( "reports/result.txt" ) );
    }
}
