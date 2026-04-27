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
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.dialect.AbstractDialect;
import org.thymeleaf.dialect.IPostProcessorDialect;
import org.thymeleaf.dialect.IPreProcessorDialect;
import org.thymeleaf.engine.AbstractTemplateHandler;
import org.thymeleaf.model.ITemplateEnd;
import org.thymeleaf.model.ITemplateStart;
import org.thymeleaf.postprocessor.IPostProcessor;
import org.thymeleaf.postprocessor.PostProcessor;
import org.thymeleaf.preprocessor.IPreProcessor;
import org.thymeleaf.preprocessor.PreProcessor;
import org.thymeleaf.templatemode.TemplateMode;

import static org.assertj.core.api.Assertions.assertThat;

public class TemplateManagerTest {

    @Test
    void processTemplateInstantiatesPreAndPostProcessorHandlers() {
        TrackingPreProcessorHandler.reset();
        TrackingPostProcessorHandler.reset();

        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.addDialect(new TrackingPreAndPostProcessorDialect());

        Context context = new Context();
        context.setVariable("name", "Thymeleaf");

        String output = templateEngine.process("<p th:text=\"${name}\">ignored</p>", context);

        assertThat(output).isEqualTo("<p>Thymeleaf</p>");
        assertThat(TrackingPreProcessorHandler.instantiationCount()).isEqualTo(1);
        assertThat(TrackingPreProcessorHandler.templateStartCount()).isGreaterThan(0);
        assertThat(TrackingPostProcessorHandler.instantiationCount()).isEqualTo(1);
        assertThat(TrackingPostProcessorHandler.templateEndCount()).isGreaterThan(0);
    }

    private static final class TrackingPreAndPostProcessorDialect extends AbstractDialect
            implements IPreProcessorDialect, IPostProcessorDialect {

        private TrackingPreAndPostProcessorDialect() {
            super("tracking-pre-and-post-processor-dialect");
        }

        @Override
        public int getDialectPreProcessorPrecedence() {
            return 100;
        }

        @Override
        public Set<IPreProcessor> getPreProcessors() {
            return Collections.singleton(new PreProcessor(TemplateMode.HTML, TrackingPreProcessorHandler.class, 10));
        }

        @Override
        public int getDialectPostProcessorPrecedence() {
            return 100;
        }

        @Override
        public Set<IPostProcessor> getPostProcessors() {
            return Collections.singleton(new PostProcessor(TemplateMode.HTML, TrackingPostProcessorHandler.class, 10));
        }
    }

    public static final class TrackingPreProcessorHandler extends AbstractTemplateHandler {

        private static int instantiationCount;
        private static int templateStartCount;

        public TrackingPreProcessorHandler() {
            super();
            instantiationCount++;
        }

        static void reset() {
            instantiationCount = 0;
            templateStartCount = 0;
        }

        static int instantiationCount() {
            return instantiationCount;
        }

        static int templateStartCount() {
            return templateStartCount;
        }

        @Override
        public void handleTemplateStart(final ITemplateStart templateStart) {
            templateStartCount++;
            super.handleTemplateStart(templateStart);
        }
    }

    public static final class TrackingPostProcessorHandler extends AbstractTemplateHandler {

        private static int instantiationCount;
        private static int templateEndCount;

        public TrackingPostProcessorHandler() {
            super();
            instantiationCount++;
        }

        static void reset() {
            instantiationCount = 0;
            templateEndCount = 0;
        }

        static int instantiationCount() {
            return instantiationCount;
        }

        static int templateEndCount() {
            return templateEndCount;
        }

        @Override
        public void handleTemplateEnd(final ITemplateEnd templateEnd) {
            templateEndCount++;
            super.handleTemplateEnd(templateEnd);
        }
    }

}
