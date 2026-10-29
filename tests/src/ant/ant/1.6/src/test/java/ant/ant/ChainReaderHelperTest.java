/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.Reader;
import java.io.StringReader;
import java.util.Vector;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.filters.util.ChainReaderHelper;
import org.apache.tools.ant.types.AntFilterReader;
import org.apache.tools.ant.types.FilterChain;
import org.apache.tools.ant.types.Parameter;
import org.junit.jupiter.api.Test;

public class ChainReaderHelperTest {
    @Test
    void constructsAndConfiguresFilterReaderByName() throws Exception {
        Project project = new Project();
        AntFilterReader filter = new AntFilterReader();
        filter.setProject(project);
        filter.setClassName("org.apache.tools.ant.filters.StripLineBreaks");
        Parameter parameter = new Parameter();
        parameter.setName("linebreaks");
        parameter.setValue("\r\n");
        filter.addParam(parameter);
        FilterChain chain = new FilterChain();
        chain.addFilterReader(filter);
        Vector<FilterChain> chains = new Vector<>();
        chains.add(chain);
        ChainReaderHelper helper = new ChainReaderHelper();
        helper.setProject(project);
        helper.setPrimaryReader(new StringReader("first\r\nsecond\n"));
        helper.setFilterChains(chains);
        try (Reader reader = helper.getAssembledReader()) {
            assertEquals("firstsecond", helper.readFully(reader));
        }
    }
}
