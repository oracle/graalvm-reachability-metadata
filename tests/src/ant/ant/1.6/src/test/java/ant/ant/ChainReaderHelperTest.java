/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Vector;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.filters.PrefixLines;
import org.apache.tools.ant.filters.util.ChainReaderHelper;
import org.apache.tools.ant.types.AntFilterReader;
import org.apache.tools.ant.types.FilterChain;
import org.apache.tools.ant.types.Parameter;
import org.apache.tools.ant.types.Path;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ChainReaderHelperTest {
    @Test
    void assemblesFilterReaderLoadedByClassName() throws IOException {
        ChainReaderHelper helper = newHelper(new StringReader("alpha\nbeta"));
        helper.setFilterChains(filterChains(filterReader("direct: ", null)));

        Reader assembledReader = helper.getAssembledReader();

        assertThat(helper.readFully(assembledReader)).isEqualTo("direct: alpha\ndirect: beta");
    }

    @Test
    void assemblesFilterReaderLoadedThroughConfiguredAntClasspath() throws IOException {
        Project project = new Project();
        ChainReaderHelper helper = newHelper(new StringReader("gamma"));
        helper.setProject(project);
        Path classpath = new Path(project);
        helper.setFilterChains(filterChains(filterReader("loader: ", classpath, project)));

        try {
            Reader assembledReader = helper.getAssembledReader();

            assertThat(helper.readFully(assembledReader)).isEqualTo("loader: gamma");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private ChainReaderHelper newHelper(Reader primaryReader) {
        ChainReaderHelper helper = new ChainReaderHelper();
        helper.setPrimaryReader(primaryReader);
        return helper;
    }

    private Vector<FilterChain> filterChains(AntFilterReader filterReader) {
        FilterChain filterChain = new FilterChain();
        filterChain.addFilterReader(filterReader);

        Vector<FilterChain> filterChains = new Vector<>();
        filterChains.addElement(filterChain);
        return filterChains;
    }

    private AntFilterReader filterReader(String prefix, Path classpath) {
        return filterReader(prefix, classpath, null);
    }

    private AntFilterReader filterReader(String prefix, Path classpath, Project project) {
        AntFilterReader filterReader = new AntFilterReader();
        filterReader.setProject(project);
        filterReader.setClassName(PrefixLines.class.getName());
        if (classpath != null) {
            filterReader.setClasspath(classpath);
        }
        filterReader.addParam(parameter("prefix", prefix));
        return filterReader;
    }

    private Parameter parameter(String name, String value) {
        Parameter parameter = new Parameter();
        parameter.setName(name);
        parameter.setValue(value);
        return parameter;
    }
}
