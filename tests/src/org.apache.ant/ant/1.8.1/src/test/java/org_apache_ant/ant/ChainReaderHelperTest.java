/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import org.apache.tools.ant.util.VectorSet;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.filters.PrefixLines;
import org.apache.tools.ant.filters.util.ChainReaderHelper;
import org.apache.tools.ant.types.AntFilterReader;
import org.apache.tools.ant.types.FilterChain;
import org.apache.tools.ant.types.Parameter;
import org.apache.tools.ant.types.Path;
import org.junit.jupiter.api.Test;

public class ChainReaderHelperTest {
    @Test
    void appliesConfiguredFilterReader() throws IOException {
        assertThat(readWithFilter(false)).isEqualTo("[ant] first\n[ant] second\n");
    }

    @Test
    void appliesConfiguredFilterReaderFromClasspath() throws IOException {
        assertThat(readWithFilter(true)).isEqualTo("[ant] first\n[ant] second\n");
    }

    private String readWithFilter(boolean useClasspath) throws IOException {
        Project project = new Project();
        project.initProperties();
        AntFilterReader filterReader = new AntFilterReader();
        filterReader.setProject(project);
        filterReader.setClassName(PrefixLines.class.getName());
        if (useClasspath) {
            filterReader.setClasspath(new Path(project));
        }

        Parameter prefix = new Parameter();
        prefix.setName("prefix");
        prefix.setValue("[ant] ");
        filterReader.addParam(prefix);

        FilterChain filterChain = new FilterChain();
        filterChain.addFilterReader(filterReader);
        VectorSet filterChains = new VectorSet();
        filterChains.add(filterChain);

        ChainReaderHelper helper = new ChainReaderHelper();
        helper.setProject(project);
        helper.setPrimaryReader(new StringReader("first\nsecond\n"));
        helper.setFilterChains(filterChains);
        try (Reader reader = helper.getAssembledReader()) {
            return helper.readFully(reader);
        }
    }
}
