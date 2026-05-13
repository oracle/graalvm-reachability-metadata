/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu.sisu_guice;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.inject.AbstractModule;
import com.google.inject.ConfigurationException;
import com.google.inject.Guice;
import com.google.inject.ImplementedBy;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.MembersInjector;
import com.google.inject.PrivateModule;
import com.google.inject.ProvidedBy;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Scope;
import com.google.inject.ScopeAnnotation;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.matcher.Matchers;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;
import com.google.inject.spi.InjectionListener;
import com.google.inject.spi.TypeConverter;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;
import com.google.inject.util.Modules;
import org.junit.jupiter.api.Test;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Sisu_guiceTest {
    @Test
    void createsInjectorWithProviderMethodsNamedBindingsAndListenerCallbacks() {
        Injector injector = Guice.createInjector(new ApplicationModule());

        Application application = injector.getInstance(Application.class);
        Service service = injector.getInstance(Service.class);
        Service secondLookup = injector.getProvider(Service.class).get();

        assertThat(application.describe())
                .isEqualTo("sisu:external:formatter for sisu:sisu:[alpha, beta]");
        assertThat(application.serviceFromProvider())
                .isSameAs(service)
                .isSameAs(secondLookup);
        assertThat(application.wasInitialized())
                .isTrue();
        assertThat(injector.findBindingsByType(TypeLiteral.get(Service.class)))
                .hasSize(1);
        assertThat(injector.getAllBindings())
                .containsKey(Key.get(Application.class));
    }

    @Test
    void injectsExistingInstancesMembersAndStaticTargets() {
        StaticTarget.reset();
        StartupTask startupTask = new StartupTask();

        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bindConstant().annotatedWith(Names.named("engineName")).to("v8");
                bind(Engine.class).toInstance(new Engine("primary"));
                requestInjection(startupTask);
                requestStaticInjection(StaticTarget.class);
            }
        });

        ManualClient client = new ManualClient();
        MembersInjector<ManualClient> membersInjector = injector.getMembersInjector(ManualClient.class);
        membersInjector.injectMembers(client);

        assertThat(startupTask.summary())
                .isEqualTo("primary/v8");
        assertThat(client.summary())
                .isEqualTo("primary/v8");
        assertThat(StaticTarget.summary())
                .isEqualTo("primary:v8");
    }

    @Test
    void supportsPrivateModulesCombinedModulesOverridesAndSpiElements() {
        com.google.inject.Module privateModule = new PrivateModule() {
            @Override
            protected void configure() {
                bind(SecretService.class).to(InternalSecretService.class);
                bind(HiddenService.class).toInstance(new HiddenServiceImpl());
                expose(SecretService.class);
            }
        };
        com.google.inject.Module defaults = new AbstractModule() {
            @Override
            protected void configure() {
                bindConstant().annotatedWith(Names.named("endpoint")).to("default");
            }
        };
        com.google.inject.Module override = new AbstractModule() {
            @Override
            protected void configure() {
                bindConstant().annotatedWith(Names.named("endpoint")).to("override");
            }
        };
        com.google.inject.Module combined = Modules.combine(privateModule, Modules.override(defaults).with(override));
        List<Element> elements = Elements.getElements(combined);

        Injector injector = Guice.createInjector(Elements.getModule(elements));

        assertThat(elements)
                .isNotEmpty();
        assertThat(injector.getInstance(SecretService.class).reveal())
                .isEqualTo("classified");
        assertThat(injector.getInstance(Key.get(String.class, Names.named("endpoint"))))
                .isEqualTo("override");
        assertThatThrownBy(() -> injector.getInstance(HiddenService.class))
                .isInstanceOf(ConfigurationException.class);
    }

    @Test
    void convertsConstantsAndAppliesCustomScope() {
        CountingScope countingScope = new CountingScope();
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bindScope(TestScoped.class, countingScope);
                convertToTypes(Matchers.only(TypeLiteral.get(Port.class)), new PortConverter());
                bindConstant().annotatedWith(Names.named("port")).to("8080");
                bind(ScopedCounter.class).in(TestScoped.class);
            }
        });

        ServerSettings settings = injector.getInstance(ServerSettings.class);
        ScopedCounter firstCounter = injector.getInstance(ScopedCounter.class);
        ScopedCounter secondCounter = injector.getInstance(ScopedCounter.class);

        assertThat(settings.portNumber())
                .isEqualTo(8080);
        assertThat(firstCounter)
                .isSameAs(secondCounter);
        assertThat(firstCounter.id())
                .isEqualTo(1);
        assertThat(countingScope.createdInstances())
                .isEqualTo(1);
        assertThat(injector.getScopeBindings())
                .containsKey(TestScoped.class);
        assertThat(injector.getTypeConverterBindings())
                .isNotEmpty();
    }

    @Test
    void honorsImplementedByAndProvidedByAnnotationsForJustInTimeBindings() {
        Injector injector = Guice.createInjector();

        Calculator calculator = injector.getInstance(Calculator.class);
        ProvidedMessage message = injector.getInstance(ProvidedMessage.class);

        assertThat(calculator.add(2, 3))
                .isEqualTo(5);
        assertThat(message.text())
                .isEqualTo("created by provider");
        assertThat(injector.getExistingBinding(Key.get(Calculator.class)))
                .isNotNull();
        assertThat(injector.getBinding(ProvidedMessage.class).getProvider().get().text())
                .isEqualTo("created by provider");
    }

    @Test
    void childInjectorsInheritParentBindingsAndKeepChildBindingsIsolated() {
        SharedConfiguration sharedConfiguration = new SharedConfiguration("main");
        Injector parent = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(SharedConfiguration.class).toInstance(sharedConfiguration);
                bindConstant().annotatedWith(Names.named("tenant")).to("parent");
            }
        });
        Injector dailyChild = parent.createChildInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(ChildReport.class).toInstance(new ChildReport("daily"));
            }
        });
        Injector weeklyChild = parent.createChildInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(ChildReport.class).toInstance(new ChildReport("weekly"));
            }
        });

        ChildDashboard dailyDashboard = dailyChild.getInstance(ChildDashboard.class);
        ChildDashboard weeklyDashboard = weeklyChild.getInstance(ChildDashboard.class);

        assertThat(dailyChild.getParent())
                .isSameAs(parent);
        assertThat(dailyChild.getInstance(SharedConfiguration.class))
                .isSameAs(sharedConfiguration)
                .isSameAs(parent.getInstance(SharedConfiguration.class));
        assertThat(dailyDashboard.summary())
                .isEqualTo("main/parent/daily");
        assertThat(weeklyDashboard.summary())
                .isEqualTo("main/parent/weekly");
        assertThatThrownBy(() -> parent.getInstance(ChildDashboard.class))
                .isInstanceOf(ConfigurationException.class);
    }

    public static final class ApplicationModule extends AbstractModule {
        @Override
        protected void configure() {
            bindConstant().annotatedWith(Names.named("applicationName")).to("sisu");
            bind(new TypeLiteral<List<String>>() { }).annotatedWith(Names.named("features"))
                    .toInstance(Arrays.asList("alpha", "beta"));
            bind(ExternalConfig.class).toInstance(new ExternalConfig("external"));
            bind(Service.class).to(DefaultService.class).in(Scopes.SINGLETON);
            bind(Formatter.class).toProvider(FormatterProvider.class);
            bindListener(Matchers.only(TypeLiteral.get(Application.class)), new ListenerRegistration());
        }

        @Provides
        @Singleton
        Repository provideRepository(@Named("applicationName") String name, Service service, Formatter formatter) {
            return new Repository(name, service, formatter);
        }
    }

    public interface ListenerAware {
        void markInjected();
    }

    public static final class ListenerRegistration implements TypeListener {
        @Override
        public <I> void hear(TypeLiteral<I> type, TypeEncounter<I> encounter) {
            encounter.register(new InjectionListener<I>() {
                @Override
                public void afterInjection(I injectee) {
                    ((ListenerAware) injectee).markInjected();
                }
            });
        }
    }

    public static final class Application implements ListenerAware {
        private final Repository repository;
        private final Provider<Service> serviceProvider;
        private final List<String> features;
        private ExternalConfig config;
        private boolean listenerInvoked;

        @Inject
        public Application(Repository repository, Provider<Service> serviceProvider,
                @Named("features") List<String> features) {
            this.repository = repository;
            this.serviceProvider = serviceProvider;
            this.features = features;
        }

        @Inject
        public void initialize(ExternalConfig config) {
            this.config = config;
        }

        @Override
        public void markInjected() {
            listenerInvoked = true;
        }

        public String describe() {
            return repository.describe(config.environment(), features);
        }

        public Service serviceFromProvider() {
            return serviceProvider.get();
        }

        public boolean wasInitialized() {
            return listenerInvoked && config != null;
        }
    }

    public interface Service {
        String name();
    }

    public static final class DefaultService implements Service {
        @Override
        public String name() {
            return "sisu";
        }
    }

    public interface Formatter {
        String format(String serviceName);
    }

    public static final class FormatterProvider implements Provider<Formatter> {
        @Inject
        @Named("applicationName")
        private String applicationName;

        @Override
        public Formatter get() {
            return serviceName -> "formatter for " + applicationName + ":" + serviceName;
        }
    }

    public static final class Repository {
        private final String name;
        private final Service service;
        private final Formatter formatter;

        public Repository(String name, Service service, Formatter formatter) {
            this.name = name;
            this.service = service;
            this.formatter = formatter;
        }

        public String describe(String environment, List<String> features) {
            return name + ":" + environment + ":" + formatter.format(service.name()) + ":" + features;
        }
    }

    public static final class ExternalConfig {
        private final String environment;

        public ExternalConfig(String environment) {
            this.environment = environment;
        }

        public String environment() {
            return environment;
        }
    }

    public static final class StartupTask {
        @Inject
        private Engine engine;
        @Inject
        @Named("engineName")
        private String engineName;

        public String summary() {
            return engine.id() + "/" + engineName;
        }
    }

    public static final class ManualClient {
        private Engine engine;
        private String engineName;

        @Inject
        public void configure(Engine engine, @Named("engineName") String engineName) {
            this.engine = engine;
            this.engineName = engineName;
        }

        public String summary() {
            return engine.id() + "/" + engineName;
        }
    }

    public static final class StaticTarget {
        private static Engine engine;
        private static String engineName;

        private StaticTarget() {
        }

        @Inject
        public static void configure(Engine injectedEngine, @Named("engineName") String injectedEngineName) {
            engine = injectedEngine;
            engineName = injectedEngineName;
        }

        public static void reset() {
            engine = null;
            engineName = null;
        }

        public static String summary() {
            return engine.id() + ":" + engineName;
        }
    }

    public static final class Engine {
        private final String id;

        public Engine(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    public static final class SharedConfiguration {
        private final String profile;

        public SharedConfiguration(String profile) {
            this.profile = profile;
        }

        public String profile() {
            return profile;
        }
    }

    public static final class ChildReport {
        private final String cadence;

        public ChildReport(String cadence) {
            this.cadence = cadence;
        }

        public String cadence() {
            return cadence;
        }
    }

    public static final class ChildDashboard {
        private final SharedConfiguration sharedConfiguration;
        private final String tenant;
        private final ChildReport childReport;

        @Inject
        public ChildDashboard(SharedConfiguration sharedConfiguration, @Named("tenant") String tenant,
                ChildReport childReport) {
            this.sharedConfiguration = sharedConfiguration;
            this.tenant = tenant;
            this.childReport = childReport;
        }

        public String summary() {
            return sharedConfiguration.profile() + "/" + tenant + "/" + childReport.cadence();
        }
    }

    public interface SecretService {
        String reveal();
    }

    public static final class InternalSecretService implements SecretService {
        @Override
        public String reveal() {
            return "classified";
        }
    }

    public interface HiddenService {
    }

    public static final class HiddenServiceImpl implements HiddenService {
    }

    @ScopeAnnotation
    @Retention(RUNTIME)
    @Target({ TYPE, METHOD })
    public @interface TestScoped {
    }

    public static final class CountingScope implements Scope {
        private final Map<Key<?>, Object> scopedObjects = new HashMap<>();
        private int createdInstances;

        @Override
        public synchronized <T> Provider<T> scope(Key<T> key, Provider<T> unscoped) {
            return new Provider<T>() {
                @Override
                public T get() {
                    return getScopedObject(key, unscoped);
                }
            };
        }

        @Override
        public String toString() {
            return "CountingScope";
        }

        public int createdInstances() {
            return createdInstances;
        }

        @SuppressWarnings("unchecked")
        private synchronized <T> T getScopedObject(Key<T> key, Provider<T> unscoped) {
            if (!scopedObjects.containsKey(key)) {
                scopedObjects.put(key, unscoped.get());
                createdInstances++;
            }
            return (T) scopedObjects.get(key);
        }
    }

    public static final class PortConverter implements TypeConverter {
        @Override
        public Object convert(String value, TypeLiteral<?> toType) {
            return new Port(Integer.parseInt(value));
        }
    }

    public static final class ServerSettings {
        private final Port port;

        @Inject
        public ServerSettings(@Named("port") Port port) {
            this.port = port;
        }

        public int portNumber() {
            return port.number();
        }
    }

    public static final class Port {
        private final int number;

        public Port(int number) {
            this.number = number;
        }

        public int number() {
            return number;
        }
    }

    @TestScoped
    public static final class ScopedCounter {
        private static int nextId;
        private final int id = ++nextId;

        public int id() {
            return id;
        }
    }

    @ImplementedBy(DefaultCalculator.class)
    public interface Calculator {
        int add(int left, int right);
    }

    public static final class DefaultCalculator implements Calculator {
        @Override
        public int add(int left, int right) {
            return left + right;
        }
    }

    @ProvidedBy(ProvidedMessageProvider.class)
    public static final class ProvidedMessage {
        private final String text;

        public ProvidedMessage(String text) {
            this.text = text;
        }

        public String text() {
            return text;
        }
    }

    public static final class ProvidedMessageProvider implements Provider<ProvidedMessage> {
        @Override
        public ProvidedMessage get() {
            return new ProvidedMessage("created by provider");
        }
    }
}
