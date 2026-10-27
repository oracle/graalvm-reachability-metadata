/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_reflect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.reflect.KClass;
import kotlin.reflect.KClassifier;
import kotlin.reflect.KFunction;
import kotlin.reflect.KMutableProperty1;
import kotlin.reflect.KProperty0;
import kotlin.reflect.KProperty1;
import kotlin.reflect.KType;
import kotlin.reflect.KVisibility;
import kotlin.reflect.full.IllegalCallableAccessException;
import kotlin.reflect.full.KClasses;
import kotlin.reflect.jvm.KCallablesJvm;
import org.junit.jupiter.api.Test;

/**
 * Kotlin reflection over plain Java classes: a settings binder fills Java configuration POJOs
 * from flat `key=value` properties through the `KClass` model that kotlin-reflect builds for
 * classes without Kotlin metadata.
 *
 * Java fields surface as `KProperty1`, and as `KMutableProperty1` when they are not `final`;
 * reference-typed fields carry platform types, statics come from `staticProperties` and
 * `staticFunctions`, and a no-argument Java constructor serves `createInstance`. Private fields
 * are bound only after the binder opens them with `isAccessible`.
 */
class JavaSettingsBinderTest {

    /** Base settings every component shares, declared once in a Java superclass. */
    public static class ComponentSettings {
        public String name = "component";
        public boolean enabled = true;
    }

    /** HTTP server settings, a Java POJO with public, private, final and static members. */
    public static class ServerSettings extends ComponentSettings {
        public static final int DEFAULT_PORT = 8080;
        public static final String SCHEMA = "server.v2";

        public final String kind = "http";
        public String host = "localhost";
        public int port = DEFAULT_PORT;
        public long idleTimeoutMillis = 30_000L;
        private String secret = "changeit";

        public String secret() {
            return secret;
        }

        /** Factory for the settings the binder starts from when a profile is named. */
        public static ServerSettings forProfile(String profile) {
            ServerSettings settings = new ServerSettings();
            settings.name = "server-" + profile;
            settings.host = profile.equals("prod") ? "0.0.0.0" : "127.0.0.1";
            return settings;
        }
    }

    /** Settings with no usable no-argument constructor. */
    public static class PoolSettings {
        public int size;

        public PoolSettings(int size) {
            this.size = size;
        }
    }

    /** Credentials holder whose private field only the access check below ever opens. */
    public static class VaultSettings {
        private String token = "vault-token";
    }

    /** Binds flat properties under a prefix to the Java fields of a settings class. */
    static final class SettingsBinder {

        /** A new instance of `type`, created through `KClass.createInstance` and then bound. */
        static <T> T bind(Class<T> type, String prefix, Map<String, String> properties) {
            T settings = KClasses.createInstance(JvmClassMappingKt.getKotlinClass(type));
            bindInto(settings, prefix, properties);
            return settings;
        }

        /** Writes every property under `prefix` into the matching field of `settings`. */
        @SuppressWarnings("unchecked")
        static <T> void bindInto(T settings, String prefix, Map<String, String> properties) {
            KClass<T> kClass = (KClass<T>) JvmClassMappingKt.getKotlinClass(settings.getClass());
            for (KProperty1<T, ?> property : KClasses.getMemberProperties(kClass)) {
                String key = prefix + "." + property.getName();
                String raw = properties.get(key);
                if (raw == null) {
                    continue;
                }
                if (!(property instanceof KMutableProperty1<T, ?> mutable)) {
                    throw new IllegalStateException(key + " is read-only");
                }
                if (mutable.getVisibility() != KVisibility.PUBLIC) {
                    KCallablesJvm.setAccessible(mutable, true);
                }
                mutable.getSetter().call(settings, convert(key, raw, mutable.getReturnType()));
            }
        }

        /** The public fields of `settings` by name, as an application would print its config. */
        @SuppressWarnings("unchecked")
        static <T> Map<String, Object> dump(T settings) {
            KClass<T> kClass = (KClass<T>) JvmClassMappingKt.getKotlinClass(settings.getClass());
            Map<String, Object> values = new TreeMap<>();
            for (KProperty1<T, ?> property : KClasses.getMemberProperties(kClass)) {
                if (property.getVisibility() == KVisibility.PUBLIC) {
                    values.put(property.getName(), property.get(settings));
                }
            }
            return values;
        }

        /** The static constants of `type` by name, read through `KProperty0`. */
        static Map<String, Object> constants(Class<?> type) {
            Map<String, Object> values = new TreeMap<>();
            for (KProperty0<?> property : KClasses.getStaticProperties(JvmClassMappingKt.getKotlinClass(type))) {
                values.put(property.getName(), property.get());
            }
            return values;
        }

        /** Calls the static factory `name` of `type` with `argument`, through `staticFunctions`. */
        static <T> T fromFactory(Class<T> type, String name, String argument) {
            KClass<T> kClass = JvmClassMappingKt.getKotlinClass(type);
            for (KFunction<?> function : KClasses.getStaticFunctions(kClass)) {
                if (function.getName().equals(name)) {
                    return KClasses.cast(kClass, function.call(argument));
                }
            }
            throw new IllegalArgumentException("no static factory " + name + " on " + kClass.getQualifiedName());
        }

        private static Object convert(String key, String raw, KType type) {
            KClassifier classifier = type.getClassifier();
            if (JvmClassMappingKt.getKotlinClass(String.class).equals(classifier)) {
                return raw;
            }
            if (JvmClassMappingKt.getKotlinClass(Integer.class).equals(classifier)) {
                return Integer.parseInt(raw);
            }
            if (JvmClassMappingKt.getKotlinClass(Long.class).equals(classifier)) {
                return Long.parseLong(raw);
            }
            if (JvmClassMappingKt.getKotlinClass(Boolean.class).equals(classifier)) {
                return Boolean.parseBoolean(raw);
            }
            throw new IllegalArgumentException("unsupported type " + type + " for " + key);
        }
    }

    @Test
    void bindsJavaFieldsIncludingInheritedAndPrivateOnes() {
        Map<String, String> properties = Map.of(
                "server.name", "edge",
                "server.enabled", "false",
                "server.host", "api.example.com",
                "server.port", "9443",
                "server.idleTimeoutMillis", "120000",
                "server.secret", "s3cr3t",
                "cache.port", "6379");

        ServerSettings settings = SettingsBinder.bind(ServerSettings.class, "server", properties);

        assertThat(settings.name).isEqualTo("edge");
        assertThat(settings.enabled).isFalse();
        assertThat(settings.host).isEqualTo("api.example.com");
        assertThat(settings.port).isEqualTo(9443);
        assertThat(settings.idleTimeoutMillis).isEqualTo(120_000L);
        assertThat(settings.secret()).isEqualTo("s3cr3t");
        assertThat(SettingsBinder.dump(settings)).containsExactly(
                Map.entry("enabled", false),
                Map.entry("host", "api.example.com"),
                Map.entry("idleTimeoutMillis", 120_000L),
                Map.entry("kind", "http"),
                Map.entry("name", "edge"),
                Map.entry("port", 9443));
    }

    @Test
    void describesJavaFieldsInKotlinTerms() {
        KClass<ServerSettings> kClass = JvmClassMappingKt.getKotlinClass(ServerSettings.class);
        Map<String, String> declared = new TreeMap<>();
        for (KProperty1<ServerSettings, ?> property : KClasses.getDeclaredMemberProperties(kClass)) {
            String mutability = property instanceof KMutableProperty1 ? "var" : "val";
            declared.put(property.getName(), property.getVisibility() + " " + mutability + " " + property.getReturnType());
        }

        assertThat(declared).containsExactly(
                Map.entry("host", "PUBLIC var kotlin.String!"),
                Map.entry("idleTimeoutMillis", "PUBLIC var kotlin.Long"),
                Map.entry("kind", "PUBLIC val kotlin.String!"),
                Map.entry("port", "PUBLIC var kotlin.Int"),
                Map.entry("secret", "PRIVATE var kotlin.String!"));
        List<String> superclasses = new ArrayList<>();
        for (KClass<?> superclass : KClasses.getSuperclasses(kClass)) {
            superclasses.add(superclass.getQualifiedName());
        }
        assertThat(superclasses).containsExactly(ComponentSettings.class.getCanonicalName());
        assertThat(kClass.isData()).isFalse();
        assertThat(kClass.getObjectInstance()).isNull();
    }

    @Test
    void readsStaticConstantsAndCallsStaticFactories() {
        assertThat(SettingsBinder.constants(ServerSettings.class)).containsExactly(
                Map.entry("DEFAULT_PORT", 8080),
                Map.entry("SCHEMA", "server.v2"));

        ServerSettings settings = SettingsBinder.fromFactory(ServerSettings.class, "forProfile", "prod");
        SettingsBinder.bindInto(settings, "server", Map.of("server.port", "443"));

        assertThat(settings.name).isEqualTo("server-prod");
        assertThat(settings.host).isEqualTo("0.0.0.0");
        assertThat(settings.port).isEqualTo(443);
        assertThatThrownBy(() -> SettingsBinder.fromFactory(ServerSettings.class, "forRegion", "eu"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("no static factory forRegion on ");
    }

    @Test
    void castsThroughTheKotlinClassModel() {
        KClass<ComponentSettings> base = JvmClassMappingKt.getKotlinClass(ComponentSettings.class);
        Object server = new ServerSettings();
        Object pool = new PoolSettings(4);

        assertThat(base.isInstance(server)).isTrue();
        assertThat(KClasses.safeCast(base, server)).isSameAs(server);
        assertThat(KClasses.safeCast(base, pool)).isNull();
        assertThat(KClasses.isSubclassOf(JvmClassMappingKt.getKotlinClass(ServerSettings.class), base)).isTrue();
        assertThatThrownBy(() -> KClasses.cast(base, pool))
                .isInstanceOf(ClassCastException.class);
    }

    @Test
    void rejectsReadOnlyFieldsAndMissingDefaultConstructors() {
        assertThatThrownBy(() -> SettingsBinder.bind(ServerSettings.class, "server", Map.of("server.kind", "grpc")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("server.kind is read-only");
        assertThatThrownBy(() -> SettingsBinder.bind(PoolSettings.class, "pool", Map.of("pool.size", "8")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("should have a single no-arg constructor");

        PoolSettings pool = new PoolSettings(2);
        SettingsBinder.bindInto(pool, "pool", Map.of("pool.size", "16"));
        assertThat(pool.size).isEqualTo(16);
    }

    @Test
    void privateFieldsStayClosedUntilOpened() {
        KClass<VaultSettings> kClass = JvmClassMappingKt.getKotlinClass(VaultSettings.class);
        KProperty1<VaultSettings, ?> token = null;
        for (KProperty1<VaultSettings, ?> property : KClasses.getDeclaredMemberProperties(kClass)) {
            if (property.getName().equals("token")) {
                token = property;
            }
        }
        assertThat(token).isNotNull();
        KProperty1<VaultSettings, ?> closed = token;
        VaultSettings settings = new VaultSettings();

        assertThat(closed.getVisibility()).isEqualTo(KVisibility.PRIVATE);
        assertThat(KCallablesJvm.isAccessible(closed)).isFalse();
        assertThatThrownBy(() -> closed.get(settings))
                .isInstanceOf(IllegalCallableAccessException.class);

        KCallablesJvm.setAccessible(closed, true);
        assertThat(KCallablesJvm.isAccessible(closed)).isTrue();
        assertThat(closed.get(settings)).isEqualTo("vault-token");
    }
}
