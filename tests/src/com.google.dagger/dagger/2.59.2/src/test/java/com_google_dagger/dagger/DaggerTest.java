/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_dagger.dagger;

import dagger.Binds;
import dagger.BindsInstance;
import dagger.BindsOptionalOf;
import dagger.Component;
import dagger.Lazy;
import dagger.MapKey;
import dagger.MembersInjector;
import dagger.Module;
import dagger.Provides;
import dagger.Reusable;
import dagger.Subcomponent;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.multibindings.IntoMap;
import dagger.multibindings.IntoSet;
import dagger.multibindings.StringKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Qualifier;
import javax.inject.Singleton;

import static org.assertj.core.api.Assertions.assertThat;

public class DaggerTest {
    @BeforeEach
    void resetCounters() {
        ExpensiveCounter.reset();
        RequestScopedValue.reset();
    }

    @Test
    void componentBuilderBindsInstancesScopesLazyValuesAndInjectsMembers() {
        ApplicationComponent component = DaggerDaggerTest_ApplicationComponent.builder()
                .applicationName("Inventory")
                .requestId("request-7")
                .build();

        Dashboard dashboard = component.dashboard();

        assertThat(dashboard.description()).isEqualTo("Inventory/request-7");
        assertThat(dashboard.format("created")).isEqualTo("Inventory:created");
        assertThat(ExpensiveCounter.created()).isZero();

        ExpensiveCounter firstCounter = component.expensiveCounterLazy().get();
        ExpensiveCounter secondCounter = component.expensiveCounterLazy().get();

        assertThat(firstCounter).isSameAs(secondCounter);
        assertThat(firstCounter.identity()).isEqualTo("counter-1");
        assertThat(ExpensiveCounter.created()).isEqualTo(1);
        assertThat(dashboard.renderWithCounter("created")).isEqualTo("Inventory:created@counter-1");
        assertThat(dashboard.providerCreatesFreshValues()).isTrue();

        ReportTarget target = new ReportTarget("daily");
        MembersInjector<ReportTarget> membersInjector = component.reportTargetMembersInjector();
        membersInjector.injectMembers(target);

        assertThat(target.formattedName()).isEqualTo("Inventory:daily");
        assertThat(target.requestId()).isEqualTo("request-7");
    }

    @Test
    void componentFactoryProvidesMultibindingsOptionalBindingsReusableTypesAndAssistedFactories() {
        FactoryComponent component = DaggerDaggerTest_FactoryComponent.factory()
                .create("Operations");

        Set<String> commandNames = component.commands().stream()
                .map(Command::name)
                .collect(Collectors.toSet());
        assertThat(commandNames).containsExactlyInAnyOrder("start", "stop");

        assertThat(component.commandsByEnumKey())
                .containsOnlyKeys(CommandName.START, CommandName.STOP);
        assertThat(component.commandsByEnumKey().get(CommandName.START).execute())
                .isEqualTo("start:Operations");
        assertThat(component.commandsByEnumKey().get(CommandName.STOP).execute())
                .isEqualTo("stop:Operations");

        Map<String, Provider<Command>> providersByStringKey = component.commandProvidersByStringKey();
        assertThat(providersByStringKey).containsOnlyKeys("start", "stop");
        assertThat(providersByStringKey.get("start").get().execute()).isEqualTo("start:Operations");
        assertThat(providersByStringKey.get("stop").get().execute()).isEqualTo("stop:Operations");

        assertThat(component.plugin())
                .isPresent()
                .get()
                .extracting(Plugin::identifier)
                .isEqualTo("default-plugin");

        AssistedReport report = component.assistedReportFactory().create("orders", 3);
        assertThat(report.summary()).isEqualTo("Operations:orders#3");
    }

    @Test
    void parentComponentExposesAbsentOptionalAndSubcomponentFactoryInheritsBindings() {
        ParentComponent parent = DaggerDaggerTest_ParentComponent.factory()
                .create("Console");

        assertThat(parent.plugin()).isEmpty();

        Session firstSession = parent.sessionComponentFactory()
                .create("session-a")
                .session();
        Session secondSession = parent.sessionComponentFactory()
                .create("session-b")
                .session();

        assertThat(firstSession.label()).isEqualTo("Console:session-a:active");
        assertThat(secondSession.label()).isEqualTo("Console:session-b:active");
    }

    @Test
    void componentDependencyExposesBindingsToDependentComponent() {
        ConfigurationComponent configuration = DaggerDaggerTest_ConfigurationComponent.factory()
                .create("Analytics", new FeatureFlags(true));
        DependentComponent dependent = DaggerDaggerTest_DependentComponent.factory()
                .create(configuration);

        assertThat(dependent.featureService().status()).isEqualTo("Analytics feature enabled");
    }

    @Test
    void componentFactoryAcceptsModuleInstancesAndIncludedModules() {
        GreetingComponent component = DaggerDaggerTest_GreetingComponent.factory()
                .create(new RuntimeGreetingModule("Hello"));

        assertThat(component.greetingCard().render("Dagger")).isEqualTo("Hello, Dagger!");
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @interface ApplicationName {
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @interface RequestId {
    }

    @MapKey
    @Retention(RetentionPolicy.RUNTIME)
    @interface CommandKey {
        CommandName value();
    }

    enum CommandName {
        START,
        STOP
    }

    @Singleton
    @Component(modules = FormatterBindingModule.class)
    interface ApplicationComponent {
        Dashboard dashboard();

        Lazy<ExpensiveCounter> expensiveCounterLazy();

        MembersInjector<ReportTarget> reportTargetMembersInjector();

        @Component.Builder
        interface Builder {
            @BindsInstance
            Builder applicationName(@ApplicationName String applicationName);

            @BindsInstance
            Builder requestId(@RequestId String requestId);

            ApplicationComponent build();
        }
    }

    @Component(modules = {
            CommandModule.class,
            FormatterBindingModule.class,
            OptionalDeclarations.class,
            PresentPluginModule.class
    })
    interface FactoryComponent {
        Set<Command> commands();

        Map<CommandName, Command> commandsByEnumKey();

        Map<String, Provider<Command>> commandProvidersByStringKey();

        Optional<Plugin> plugin();

        AssistedReportFactory assistedReportFactory();

        @Component.Factory
        interface Factory {
            FactoryComponent create(@BindsInstance @ApplicationName String applicationName);
        }
    }

    @Component(modules = {
            FormatterBindingModule.class,
            OptionalDeclarations.class,
            SessionInstaller.class
    })
    interface ParentComponent {
        Optional<Plugin> plugin();

        SessionComponent.Factory sessionComponentFactory();

        @Component.Factory
        interface Factory {
            ParentComponent create(@BindsInstance @ApplicationName String applicationName);
        }
    }

    @Component
    interface ConfigurationComponent {
        @ApplicationName
        String applicationName();

        FeatureFlags featureFlags();

        @Component.Factory
        interface Factory {
            ConfigurationComponent create(
                    @BindsInstance @ApplicationName String applicationName,
                    @BindsInstance FeatureFlags featureFlags);
        }
    }

    @Component(dependencies = ConfigurationComponent.class)
    interface DependentComponent {
        FeatureService featureService();

        @Component.Factory
        interface Factory {
            DependentComponent create(ConfigurationComponent configurationComponent);
        }
    }

    @Component(modules = RuntimeGreetingModule.class)
    interface GreetingComponent {
        GreetingCard greetingCard();

        @Component.Factory
        interface Factory {
            GreetingComponent create(RuntimeGreetingModule greetingModule);
        }
    }

    @Subcomponent(modules = SessionModule.class)
    interface SessionComponent {
        Session session();

        @Subcomponent.Factory
        interface Factory {
            SessionComponent create(@BindsInstance @RequestId String requestId);
        }
    }

    @Module
    interface FormatterBindingModule {
        @Binds
        Formatter bindFormatter(PrefixFormatter formatter);
    }

    @Module
    interface CommandModule {
        @Binds
        @IntoSet
        Command bindStartCommandIntoSet(StartCommand command);

        @Binds
        @IntoSet
        Command bindStopCommandIntoSet(StopCommand command);

        @Binds
        @IntoMap
        @CommandKey(CommandName.START)
        Command bindStartCommandByEnumKey(StartCommand command);

        @Binds
        @IntoMap
        @CommandKey(CommandName.STOP)
        Command bindStopCommandByEnumKey(StopCommand command);

        @Binds
        @IntoMap
        @StringKey("start")
        Command bindStartCommandByStringKey(StartCommand command);

        @Binds
        @IntoMap
        @StringKey("stop")
        Command bindStopCommandByStringKey(StopCommand command);
    }

    @Module
    interface OptionalDeclarations {
        @BindsOptionalOf
        Plugin optionalPlugin();
    }

    @Module
    interface PresentPluginModule {
        @Binds
        Plugin bindPlugin(DefaultPlugin plugin);
    }

    @Module(subcomponents = SessionComponent.class)
    interface SessionInstaller {
    }

    @Module
    static final class SessionModule {
        private SessionModule() {
        }

        @Provides
        static SessionSuffix sessionSuffix() {
            return new SessionSuffix("active");
        }
    }

    @Module(includes = GreetingPunctuationModule.class)
    static final class RuntimeGreetingModule {
        private final String salutation;

        RuntimeGreetingModule(String salutation) {
            this.salutation = salutation;
        }

        @Provides
        GreetingPrefix greetingPrefix() {
            return new GreetingPrefix(salutation);
        }
    }

    @Module
    static final class GreetingPunctuationModule {
        private GreetingPunctuationModule() {
        }

        @Provides
        static GreetingPunctuation greetingPunctuation() {
            return new GreetingPunctuation("!");
        }
    }

    interface Formatter {
        String format(String value);
    }

    static final class PrefixFormatter implements Formatter {
        private final String applicationName;

        @Inject
        PrefixFormatter(@ApplicationName String applicationName) {
            this.applicationName = applicationName;
        }

        @Override
        public String format(String value) {
            return applicationName + ":" + value;
        }
    }

    static final class Dashboard {
        private final String applicationName;
        private final RequestContext requestContext;
        private final Formatter formatter;
        private final Lazy<ExpensiveCounter> counter;
        private final Provider<RequestScopedValue> valueProvider;

        @Inject
        Dashboard(
                @ApplicationName String applicationName,
                RequestContext requestContext,
                Formatter formatter,
                Lazy<ExpensiveCounter> counter,
                Provider<RequestScopedValue> valueProvider) {
            this.applicationName = applicationName;
            this.requestContext = requestContext;
            this.formatter = formatter;
            this.counter = counter;
            this.valueProvider = valueProvider;
        }

        String description() {
            return applicationName + "/" + requestContext.id();
        }

        String format(String value) {
            return formatter.format(value);
        }

        String renderWithCounter(String value) {
            return formatter.format(value) + "@" + counter.get().identity();
        }

        boolean providerCreatesFreshValues() {
            return valueProvider.get().sequence() != valueProvider.get().sequence();
        }
    }

    static final class RequestContext {
        private final String id;

        @Inject
        RequestContext(@RequestId String id) {
            this.id = id;
        }

        String id() {
            return id;
        }
    }

    @Singleton
    static final class ExpensiveCounter {
        private static final AtomicInteger CREATED = new AtomicInteger();

        private final int sequence;

        @Inject
        ExpensiveCounter() {
            this.sequence = CREATED.incrementAndGet();
        }

        static void reset() {
            CREATED.set(0);
        }

        static int created() {
            return CREATED.get();
        }

        String identity() {
            return "counter-" + sequence;
        }
    }

    static final class RequestScopedValue {
        private static final AtomicInteger CREATED = new AtomicInteger();

        private final int sequence;

        @Inject
        RequestScopedValue() {
            this.sequence = CREATED.incrementAndGet();
        }

        static void reset() {
            CREATED.set(0);
        }

        int sequence() {
            return sequence;
        }
    }

    static final class ReportTarget {
        private final String name;

        @Inject
        Formatter formatter;

        @Inject
        RequestContext requestContext;

        ReportTarget(String name) {
            this.name = name;
        }

        String formattedName() {
            return formatter.format(name);
        }

        String requestId() {
            return requestContext.id();
        }
    }

    interface Command {
        String name();

        String execute();
    }

    @Reusable
    static final class StartCommand implements Command {
        private final String applicationName;

        @Inject
        StartCommand(@ApplicationName String applicationName) {
            this.applicationName = applicationName;
        }

        @Override
        public String name() {
            return "start";
        }

        @Override
        public String execute() {
            return "start:" + applicationName;
        }
    }

    static final class StopCommand implements Command {
        private final String applicationName;

        @Inject
        StopCommand(@ApplicationName String applicationName) {
            this.applicationName = applicationName;
        }

        @Override
        public String name() {
            return "stop";
        }

        @Override
        public String execute() {
            return "stop:" + applicationName;
        }
    }

    interface Plugin {
        String identifier();
    }

    static final class DefaultPlugin implements Plugin {
        @Inject
        DefaultPlugin() {
        }

        @Override
        public String identifier() {
            return "default-plugin";
        }
    }

    static final class AssistedReport {
        private final Formatter formatter;
        private final String subject;
        private final int lineCount;

        @AssistedInject
        AssistedReport(
                Formatter formatter,
                @Assisted String subject,
                @Assisted int lineCount) {
            this.formatter = formatter;
            this.subject = subject;
            this.lineCount = lineCount;
        }

        String summary() {
            return formatter.format(subject) + "#" + lineCount;
        }
    }

    @AssistedFactory
    interface AssistedReportFactory {
        AssistedReport create(String subject, int lineCount);
    }

    static final class Session {
        private final Formatter formatter;
        private final RequestContext requestContext;
        private final SessionSuffix suffix;

        @Inject
        Session(Formatter formatter, RequestContext requestContext, SessionSuffix suffix) {
            this.formatter = formatter;
            this.requestContext = requestContext;
            this.suffix = suffix;
        }

        String label() {
            return formatter.format(requestContext.id()) + ":" + suffix.value();
        }
    }

    static final class SessionSuffix {
        private final String value;

        SessionSuffix(String value) {
            this.value = value;
        }

        String value() {
            return value;
        }
    }

    static final class GreetingCard {
        private final GreetingPrefix prefix;
        private final GreetingPunctuation punctuation;

        @Inject
        GreetingCard(GreetingPrefix prefix, GreetingPunctuation punctuation) {
            this.prefix = prefix;
            this.punctuation = punctuation;
        }

        String render(String recipient) {
            return prefix.value() + ", " + recipient + punctuation.value();
        }
    }

    static final class GreetingPrefix {
        private final String value;

        GreetingPrefix(String value) {
            this.value = value;
        }

        String value() {
            return value;
        }
    }

    static final class GreetingPunctuation {
        private final String value;

        GreetingPunctuation(String value) {
            this.value = value;
        }

        String value() {
            return value;
        }
    }

    static final class FeatureFlags {
        private final boolean featureEnabled;

        FeatureFlags(boolean featureEnabled) {
            this.featureEnabled = featureEnabled;
        }

        boolean featureEnabled() {
            return featureEnabled;
        }
    }

    static final class FeatureService {
        private final String applicationName;
        private final FeatureFlags featureFlags;

        @Inject
        FeatureService(@ApplicationName String applicationName, FeatureFlags featureFlags) {
            this.applicationName = applicationName;
            this.featureFlags = featureFlags;
        }

        String status() {
            return applicationName + " feature " + (featureFlags.featureEnabled() ? "enabled" : "disabled");
        }
    }
}
