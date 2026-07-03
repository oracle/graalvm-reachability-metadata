/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_ai.spring_ai_model;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.execution.DefaultToolCallResultConverter;
import org.springframework.ai.tool.execution.ToolCallResultConverter;
import org.springframework.ai.tool.support.ToolUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class ToolUtilsTest {

    @Test
    void instantiatesResultConverterDeclaredByToolAnnotation() throws NoSuchMethodException {
        Method toolMethod = WeatherTools.class.getMethod("currentWeather");

        ToolCallResultConverter converter = ToolUtils.getToolCallResultConverter(toolMethod);

        assertThat(converter).isExactlyInstanceOf(DefaultToolCallResultConverter.class);
    }

    public static final class WeatherTools {

        @Tool
        public String currentWeather() {
            return "sunny";
        }

    }

}
