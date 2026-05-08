/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_puppycrawl_tools.checkstyle;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.net.URI;

import com.puppycrawl.tools.checkstyle.api.LineColumn;
import com.puppycrawl.tools.checkstyle.utils.CommonUtil;
import org.junit.jupiter.api.Test;

public class CommonUtilTest {
    private static final String RESOURCE_NAME = "com_puppycrawl_tools/checkstyle/"
        + "common-util-resource.txt";

    @Test
    void constructorHelpersCreateNewInstance() {
        Constructor<LineColumn> constructor = CommonUtil.getConstructor(
            LineColumn.class, int.class, int.class);

        LineColumn lineColumn = CommonUtil.invokeConstructor(constructor, 12, 4);

        assertThat(lineColumn.getLine()).isEqualTo(12);
        assertThat(lineColumn.getColumn()).isEqualTo(4);
    }

    @Test
    void resolvesAbsoluteClasspathResourceName() throws Exception {
        URI uri = CommonUtil.getUriByFilename('/' + RESOURCE_NAME);

        assertThat(uri.toString()).contains("common-util-resource.txt");
    }

    @Test
    void resolvesRelativeClasspathResourceName() throws Exception {
        URI uri = CommonUtil.getUriByFilename(RESOURCE_NAME);

        assertThat(uri.toString()).contains("common-util-resource.txt");
    }
}
