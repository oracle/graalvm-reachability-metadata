/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_thymeleaf.thymeleaf;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.dialect.AbstractDialect;
import org.thymeleaf.dialect.IPostProcessorDialect;
import org.thymeleaf.dialect.IPreProcessorDialect;
import org.thymeleaf.engine.AbstractTemplateHandler;
import org.thymeleaf.model.ITemplateStart;
import org.thymeleaf.postprocessor.IPostProcessor;
import org.thymeleaf.postprocessor.PostProcessor;
import org.thymeleaf.preprocessor.IPreProcessor;
import org.thymeleaf.preprocessor.PreProcessor;
import org.thymeleaf.templatemode.TemplateMode;

import static org.assertj.core.api.Assertions.assertThat;

public class TemplateManagerTest {

    private static final AtomicInteger PRE_PROCESSOR_INSTANTIATIONS = new AtomicInteger();
    private static final AtomicInteger PRE_PROCESSOR_TEMPLATE_STARTS = new AtomicInteger();
    private static final AtomicInteger POST_PROCESSOR_INSTANTIATIONS = new AtomicInteger();
    private static final AtomicInteger POST_PROCESSOR_TEMPLATE_STARTS = new AtomicInteger();

    @BeforeEach
    void resetCounters() {
        PRE_PROCESSOR_INSTANTIATIONS.set(0);
        PRE_PROCESSOR_TEMPLATE_STARTS.set(0);
        POST_PROCESSOR_INSTANTIATIONS.set(0);
        POST_PROCESSOR_TEMPLATE_STARTS.set(0);
    }

    @Test
    void processInstantiatesConfiguredPreProcessorHandler() {
        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.addDialect(new TemplateManagerPreProcessorDialect());

        String output = templateEngine.process("<p>Hello</p>", new Context());

        assertThat(output).isEqualTo("<p>Hello</p>");
        assertThat(PRE_PROCESSOR_INSTANTIATIONS.get()).isGreaterThan(0);
        assertThat(PRE_PROCESSOR_TEMPLATE_STARTS.get()).isGreaterThan(0);
    }

    @Test
    void processInstantiatesConfiguredPostProcessorHandler() {
        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.addDialect(new TemplateManagerPostProcessorDialect());

        String output = templateEngine.process("<p>Hello</p>", new Context());

        assertThat(output).isEqualTo("<p>Hello</p>");
        assertThat(POST_PROCESSOR_INSTANTIATIONS.get()).isGreaterThan(0);
        assertThat(POST_PROCESSOR_TEMPLATE_STARTS.get()).isGreaterThan(0);
    }

    private static final class TemplateManagerPreProcessorDialect extends AbstractDialect implements IPreProcessorDialect {

        private TemplateManagerPreProcessorDialect() {
            super("TemplateManager pre-processor dialect");
        }

        @Override
        public int getDialectPreProcessorPrecedence() {
            return 1000;
        }

        @Override
        public Set<IPreProcessor> getPreProcessors() {
            return Set.of(new PreProcessor(TemplateMode.HTML, TemplateManagerPreProcessorHandler.class, 1000));
        }
    }

    private static final class TemplateManagerPostProcessorDialect extends AbstractDialect implements IPostProcessorDialect {

        private TemplateManagerPostProcessorDialect() {
            super("TemplateManager post-processor dialect");
        }

        @Override
        public int getDialectPostProcessorPrecedence() {
            return 1000;
        }

        @Override
        public Set<IPostProcessor> getPostProcessors() {
            return Set.of(new PostProcessor(TemplateMode.HTML, TemplateManagerPostProcessorHandler.class, 1000));
        }
    }

    public static final class TemplateManagerPreProcessorHandler extends AbstractTemplateHandler {

        public TemplateManagerPreProcessorHandler() {
            PRE_PROCESSOR_INSTANTIATIONS.incrementAndGet();
        }

        @Override
        public void handleTemplateStart(ITemplateStart templateStart) {
            PRE_PROCESSOR_TEMPLATE_STARTS.incrementAndGet();
            super.handleTemplateStart(templateStart);
        }
    }

    public static final class TemplateManagerPostProcessorHandler extends AbstractTemplateHandler {

        public TemplateManagerPostProcessorHandler() {
            POST_PROCESSOR_INSTANTIATIONS.incrementAndGet();
        }

        @Override
        public void handleTemplateStart(ITemplateStart templateStart) {
            POST_PROCESSOR_TEMPLATE_STARTS.incrementAndGet();
            super.handleTemplateStart(templateStart);
        }
    }
}
