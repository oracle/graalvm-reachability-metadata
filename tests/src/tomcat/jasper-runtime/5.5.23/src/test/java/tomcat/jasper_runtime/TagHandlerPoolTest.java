/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat.jasper_runtime;

import org.apache.jasper.runtime.PerThreadTagHandlerPool;
import org.apache.jasper.runtime.TagHandlerPool;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.jsp.tagext.Tag;
import javax.servlet.jsp.tagext.TagSupport;

import static org.assertj.core.api.Assertions.assertThat;

public class TagHandlerPoolTest {
    @Test
    void getTagHandlerPoolLoadsConfiguredPoolImplementation() {
        Map<String, String> initParameters = new HashMap<>();
        initParameters.put(TagHandlerPool.OPTION_TAGPOOL, PerThreadTagHandlerPool.class.getName());
        initParameters.put(TagHandlerPool.OPTION_MAXSIZE, "1");

        TagHandlerPool pool = TagHandlerPool.getTagHandlerPool(new ParameterServletConfig(initParameters));

        try {
            assertThat(pool).isInstanceOf(PerThreadTagHandlerPool.class);
        } finally {
            pool.release();
        }
    }

    @Test
    void getCreatesTagHandlerWhenPoolIsEmpty() throws Exception {
        TagHandlerPool pool = new TagHandlerPool(1);

        Tag tag = pool.get(TagSupport.class);

        assertThat(tag).isInstanceOf(TagSupport.class);
        tag.release();
    }

    private static final class ParameterServletConfig implements ServletConfig {
        private final Map<String, String> initParameters;

        private ParameterServletConfig(Map<String, String> initParameters) {
            this.initParameters = initParameters;
        }

        @Override
        public String getServletName() {
            return "tag-handler-pool-test";
        }

        @Override
        public ServletContext getServletContext() {
            return null;
        }

        @Override
        public String getInitParameter(String name) {
            return initParameters.get(name);
        }

        @Override
        public Enumeration getInitParameterNames() {
            return Collections.enumeration(initParameters.keySet());
        }
    }
}
