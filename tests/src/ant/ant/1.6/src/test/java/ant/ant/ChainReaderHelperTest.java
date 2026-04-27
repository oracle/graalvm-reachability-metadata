/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Vector;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.filters.util.ChainReaderHelper;
import org.apache.tools.ant.types.AntFilterReader;
import org.apache.tools.ant.types.FilterChain;
import org.apache.tools.ant.types.Parameter;
import org.apache.tools.ant.types.Parameterizable;
import org.apache.tools.ant.types.Path;
import org.junit.jupiter.api.Test;

public class ChainReaderHelperTest {
    @Test
    void assemblesConfiguredFilterReaderByClassName() throws IOException {
        Reader reader = assembleReader("payload", false, parameter("prefix", "["), parameter("suffix", "]"));

        assertThat(new ChainReaderHelper().readFully(reader)).isEqualTo("[payload]");
    }

    @Test
    void assemblesConfiguredFilterReaderThroughAntClassLoader() throws IOException {
        Reader reader = assembleReader("classpath", true, parameter("prefix", "loaded:"));

        assertThat(new ChainReaderHelper().readFully(reader)).isEqualTo("loaded:classpath");
    }

    private static Reader assembleReader(String input, boolean withClasspath, Parameter... parameters) {
        Project project = new Project();
        ChainReaderHelper helper = new ChainReaderHelper();
        helper.setProject(project);
        helper.setPrimaryReader(new StringReader(input));

        AntFilterReader filterReader = new AntFilterReader();
        filterReader.setProject(project);
        filterReader.setClassName(TransformingFilterReader.class.getName());
        for (Parameter parameter : parameters) {
            filterReader.addParam(parameter);
        }
        if (withClasspath) {
            filterReader.setClasspath(new Path(project));
        }

        FilterChain filterChain = new FilterChain();
        filterChain.setProject(project);
        filterChain.addFilterReader(filterReader);

        Vector filterChains = new Vector();
        filterChains.addElement(filterChain);
        helper.setFilterChains(filterChains);
        return helper.getAssembledReader();
    }

    private static Parameter parameter(String name, String value) {
        Parameter parameter = new Parameter();
        parameter.setName(name);
        parameter.setValue(value);
        return parameter;
    }

    public static final class TransformingFilterReader extends FilterReader implements Parameterizable {
        private String prefix = "";
        private String suffix = "";
        private String content;
        private int position;

        public TransformingFilterReader(Reader in) {
            super(in);
        }

        @Override
        public void setParameters(Parameter[] parameters) {
            for (Parameter parameter : parameters) {
                if ("prefix".equals(parameter.getName())) {
                    prefix = parameter.getValue();
                }
                if ("suffix".equals(parameter.getName())) {
                    suffix = parameter.getValue();
                }
            }
        }

        @Override
        public int read() throws IOException {
            ensureContentLoaded();
            if (position >= content.length()) {
                return -1;
            }
            return content.charAt(position++);
        }

        @Override
        public int read(char[] buffer, int offset, int length) throws IOException {
            ensureContentLoaded();
            if (position >= content.length()) {
                return -1;
            }
            int charactersToCopy = Math.min(length, content.length() - position);
            content.getChars(position, position + charactersToCopy, buffer, offset);
            position += charactersToCopy;
            return charactersToCopy;
        }

        private void ensureContentLoaded() throws IOException {
            if (content != null) {
                return;
            }

            StringBuilder builder = new StringBuilder(prefix);
            char[] buffer = new char[64];
            int read;
            while ((read = in.read(buffer)) != -1) {
                builder.append(buffer, 0, read);
            }
            builder.append(suffix);
            content = builder.toString();
        }
    }
}
