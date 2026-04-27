/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_thymeleaf.thymeleaf;

import java.util.Collections;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.dialect.AbstractDialect;
import org.thymeleaf.dialect.IPostProcessorDialect;
import org.thymeleaf.dialect.IPreProcessorDialect;
import org.thymeleaf.engine.ProcessorTemplateHandler;
import org.thymeleaf.postprocessor.IPostProcessor;
import org.thymeleaf.postprocessor.PostProcessor;
import org.thymeleaf.preprocessor.IPreProcessor;
import org.thymeleaf.preprocessor.PreProcessor;
import org.thymeleaf.templatemode.TemplateMode;

import static org.assertj.core.api.Assertions.assertThat;

public class DialectSetConfigurationTest {

    @Test
    void initializeConfigurationWithDialectSpecificPreAndPostProcessors() {
        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.addDialect(new PreAndPostProcessorDialect());

        IEngineConfiguration configuration = templateEngine.getConfiguration();

        assertThat(configuration.getPreProcessors(TemplateMode.HTML))
                .singleElement()
                .extracting(IPreProcessor::getHandlerClass)
                .isEqualTo(ProcessorTemplateHandler.class);
        assertThat(configuration.getPostProcessors(TemplateMode.HTML))
                .singleElement()
                .extracting(IPostProcessor::getHandlerClass)
                .isEqualTo(ProcessorTemplateHandler.class);
    }

    private static final class PreAndPostProcessorDialect extends AbstractDialect
            implements IPreProcessorDialect, IPostProcessorDialect {

        private PreAndPostProcessorDialect() {
            super("test-pre-and-post-processor-dialect");
        }

        @Override
        public int getDialectPreProcessorPrecedence() {
            return 100;
        }

        @Override
        public Set<IPreProcessor> getPreProcessors() {
            return Collections.singleton(new PreProcessor(TemplateMode.HTML, ProcessorTemplateHandler.class, 10));
        }

        @Override
        public int getDialectPostProcessorPrecedence() {
            return 100;
        }

        @Override
        public Set<IPostProcessor> getPostProcessors() {
            return Collections.singleton(new PostProcessor(TemplateMode.HTML, ProcessorTemplateHandler.class, 10));
        }
    }
}
