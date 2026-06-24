/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_core;

import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.Context;
import ch.qos.logback.core.ContextBase;
import ch.qos.logback.core.encoder.EchoEncoder;
import ch.qos.logback.core.joran.JoranConfiguratorBase;
import ch.qos.logback.core.joran.ModelClassToModelHandlerLinkerBase;
import ch.qos.logback.core.joran.action.AppenderRefAction;
import ch.qos.logback.core.joran.action.BaseModelAction;
import ch.qos.logback.core.joran.spi.ElementSelector;
import ch.qos.logback.core.joran.spi.RuleStore;
import ch.qos.logback.core.joran.spi.SaxEventInterpretationContext;
import ch.qos.logback.core.model.AppenderModel;
import ch.qos.logback.core.model.AppenderRefModel;
import ch.qos.logback.core.model.Model;
import ch.qos.logback.core.model.processor.AppenderDeclarationAnalyser;
import ch.qos.logback.core.model.processor.AppenderModelHandler;
import ch.qos.logback.core.model.processor.AppenderRefDependencyAnalyser;
import ch.qos.logback.core.model.processor.AppenderRefModelHandler;
import ch.qos.logback.core.model.processor.DefaultProcessor;
import ch.qos.logback.core.model.processor.ModelHandlerBase;
import ch.qos.logback.core.model.processor.ModelHandlerException;
import ch.qos.logback.core.model.processor.ModelInterpretationContext;
import ch.qos.logback.core.spi.AppenderAttachable;
import ch.qos.logback.core.spi.AppenderAttachableImpl;
import org.fusesource.jansi.AnsiConsole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.Attributes;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

import static org.assertj.core.api.Assertions.assertThat;

public class ConsoleAppenderTest {
    @BeforeEach
    void resetAnsiConsole() {
        AnsiConsole.reset();
    }

    @Test
    void withJansiUsesAnsiConsoleOutMethodForSystemOut() {
        ConsoleAppender<String> appender = newConsoleAppender("System.out");

        appender.start();
        appender.doAppend("jansi out branch");

        assertThat(appender.isStarted()).isTrue();
        assertThat(AnsiConsole.systemInstallCount()).isEqualTo(1);
        assertThat(AnsiConsole.outCount()).isEqualTo(1);
        assertThat(AnsiConsole.wrapSystemErrCount()).isZero();
        assertThat(AnsiConsole.outContent()).contains("jansi out branch");
    }

    @Test
    void withJansiFallsBackToWrapSystemErrWhenErrMethodIsUnavailable() {
        ConsoleAppender<String> appender = newConsoleAppender("System.err");

        appender.start();
        appender.doAppend("jansi err wrapper branch");

        assertThat(appender.isStarted()).isTrue();
        assertThat(AnsiConsole.systemInstallCount()).isEqualTo(1);
        assertThat(AnsiConsole.outCount()).isZero();
        assertThat(AnsiConsole.wrapSystemErrCount()).isEqualTo(1);
        assertThat(AnsiConsole.errContent()).contains("jansi err wrapper branch");
    }

    @Test
    void joranConfigurationSetsConsoleAppenderTarget() throws Exception {
        AttachableContext context = new AttachableContext();
        context.setName("console-appender-joran-test-context");
        CoreAppenderConfigurator configurator = new CoreAppenderConfigurator();
        configurator.setContext(context);

        configurator.doConfigure(new ByteArrayInputStream("""
                <configuration>
                    <appender name="STDERR" class="ch.qos.logback.core.ConsoleAppender">
                        <target>System.err</target>
                        <encoder class="ch.qos.logback.core.encoder.EchoEncoder"/>
                    </appender>
                    <appender-ref ref="STDERR"/>
                </configuration>
                """.getBytes(StandardCharsets.UTF_8)));

        Appender<String> appender = context.getAppender("STDERR");

        assertThat(appender).isInstanceOf(ConsoleAppender.class);
        assertThat(((ConsoleAppender<?>) appender).getTarget()).isEqualTo("System.err");
        assertThat(appender.isStarted()).isTrue();
        context.detachAndStopAllAppenders();
    }

    private static ConsoleAppender<String> newConsoleAppender(String target) {
        ContextBase context = new ContextBase();
        context.setName("console-appender-test-context");

        EchoEncoder<String> encoder = new EchoEncoder<>();
        encoder.setContext(context);
        encoder.start();

        ConsoleAppender<String> appender = new ConsoleAppender<>();
        appender.setContext(context);
        appender.setName("console-appender-test");
        appender.setEncoder(encoder);
        appender.setTarget(target);
        appender.setWithJansi(true);
        return appender;
    }

    private static final class CoreAppenderConfigurator extends JoranConfiguratorBase<String> {
        @Override
        protected void addElementSelectorAndActionAssociations(RuleStore ruleStore) {
            super.addElementSelectorAndActionAssociations(ruleStore);
            ruleStore.addRule(new ElementSelector("configuration"), ConfigurationModelAction::new);
            ruleStore.addRule(new ElementSelector("configuration/appender-ref"), AppenderRefAction::new);
        }

        @Override
        protected void addModelHandlerAssociations(DefaultProcessor defaultProcessor) {
            new CoreAppenderModelLinker(context).link(defaultProcessor);
        }
    }

    private static final class CoreAppenderModelLinker extends ModelClassToModelHandlerLinkerBase {
        private CoreAppenderModelLinker(Context context) {
            super(context);
        }

        @Override
        public void link(DefaultProcessor defaultProcessor) {
            super.link(defaultProcessor);
            defaultProcessor.addHandler(Model.class, ConfigurationModelHandler::makeInstance);
            defaultProcessor.addHandler(AppenderModel.class, AppenderModelHandler::makeInstance);
            defaultProcessor.addHandler(AppenderRefModel.class, AppenderRefModelHandler::makeInstance);
            defaultProcessor.addAnalyser(Model.class, () -> new AppenderRefDependencyAnalyser(context));
            defaultProcessor.addAnalyser(AppenderModel.class, () -> new AppenderDeclarationAnalyser(context));
            defaultProcessor.getPhaseOneFilter().deny(AppenderModel.class);
            defaultProcessor.getPhaseOneFilter().deny(AppenderRefModel.class);
        }
    }

    private static final class ConfigurationModelAction extends BaseModelAction {
        @Override
        protected Model buildCurrentModel(
                SaxEventInterpretationContext interpretationContext, String name, Attributes attributes) {
            return new Model();
        }
    }

    private static final class ConfigurationModelHandler extends ModelHandlerBase {
        private ConfigurationModelHandler(Context context) {
            super(context);
        }

        private static ModelHandlerBase makeInstance(
                Context context, ModelInterpretationContext modelInterpretationContext) {
            return new ConfigurationModelHandler(context);
        }

        @Override
        protected Class<Model> getSupportedModelClass() {
            return Model.class;
        }

        @Override
        public void handle(ModelInterpretationContext modelInterpretationContext, Model model)
                throws ModelHandlerException {
            // The top-level configuration element only groups nested appender models in this core-only test.
        }
    }

    private static final class AttachableContext extends ContextBase implements AppenderAttachable<String> {
        private final AppenderAttachableImpl<String> appenderAttachable = new AppenderAttachableImpl<>();

        @Override
        public void addAppender(Appender<String> newAppender) {
            appenderAttachable.addAppender(newAppender);
        }

        @Override
        public Iterator<Appender<String>> iteratorForAppenders() {
            return appenderAttachable.iteratorForAppenders();
        }

        @Override
        public Appender<String> getAppender(String name) {
            return appenderAttachable.getAppender(name);
        }

        @Override
        public boolean isAttached(Appender<String> appender) {
            return appenderAttachable.isAttached(appender);
        }

        @Override
        public void detachAndStopAllAppenders() {
            appenderAttachable.detachAndStopAllAppenders();
        }

        @Override
        public boolean detachAppender(Appender<String> appender) {
            return appenderAttachable.detachAppender(appender);
        }

        @Override
        public boolean detachAppender(String name) {
            return appenderAttachable.detachAppender(name);
        }
    }
}
