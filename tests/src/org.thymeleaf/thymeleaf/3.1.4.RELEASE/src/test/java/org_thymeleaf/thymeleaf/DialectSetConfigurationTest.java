/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_thymeleaf.thymeleaf;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.dialect.AbstractDialect;
import org.thymeleaf.dialect.IPostProcessorDialect;
import org.thymeleaf.dialect.IPreProcessorDialect;
import org.thymeleaf.engine.AbstractTemplateHandler;
import org.thymeleaf.engine.ITemplateHandler;
import org.thymeleaf.postprocessor.IPostProcessor;
import org.thymeleaf.postprocessor.PostProcessor;
import org.thymeleaf.preprocessor.IPreProcessor;
import org.thymeleaf.preprocessor.PreProcessor;
import org.thymeleaf.templatemode.TemplateMode;

import static org.assertj.core.api.Assertions.assertThat;

public class DialectSetConfigurationTest {

    @Test
    void configurationIncludesPreProcessorHandlersFromCustomDialects() {
        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.addDialect(new DialectSetConfigurationPreProcessorDialect());

        Set<Class<? extends ITemplateHandler>> handlerClasses = templateEngine.getConfiguration()
                .getPreProcessors(TemplateMode.HTML)
                .stream()
                .map(IPreProcessor::getHandlerClass)
                .collect(Collectors.toSet());

        assertThat(handlerClasses).contains(DialectSetConfigurationPreProcessorHandler.class);
    }

    @Test
    void configurationIncludesPostProcessorHandlersFromCustomDialects() {
        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.addDialect(new DialectSetConfigurationPostProcessorDialect());

        Set<Class<? extends ITemplateHandler>> handlerClasses = templateEngine.getConfiguration()
                .getPostProcessors(TemplateMode.HTML)
                .stream()
                .map(IPostProcessor::getHandlerClass)
                .collect(Collectors.toSet());

        assertThat(handlerClasses).contains(DialectSetConfigurationPostProcessorHandler.class);
    }
}

final class DialectSetConfigurationPreProcessorDialect extends AbstractDialect implements IPreProcessorDialect {

    DialectSetConfigurationPreProcessorDialect() {
        super("DialectSetConfiguration pre-processor dialect");
    }

    @Override
    public int getDialectPreProcessorPrecedence() {
        return 1000;
    }

    @Override
    public Set<IPreProcessor> getPreProcessors() {
        return Set.of(new PreProcessor(TemplateMode.HTML, DialectSetConfigurationPreProcessorHandler.class, 1000));
    }
}

final class DialectSetConfigurationPostProcessorDialect extends AbstractDialect implements IPostProcessorDialect {

    DialectSetConfigurationPostProcessorDialect() {
        super("DialectSetConfiguration post-processor dialect");
    }

    @Override
    public int getDialectPostProcessorPrecedence() {
        return 1000;
    }

    @Override
    public Set<IPostProcessor> getPostProcessors() {
        return Set.of(new PostProcessor(TemplateMode.HTML, DialectSetConfigurationPostProcessorHandler.class, 1000));
    }
}

final class DialectSetConfigurationPreProcessorHandler extends AbstractTemplateHandler {

    public DialectSetConfigurationPreProcessorHandler() {
        super();
    }
}

final class DialectSetConfigurationPostProcessorHandler extends AbstractTemplateHandler {

    public DialectSetConfigurationPostProcessorHandler() {
        super();
    }
}
