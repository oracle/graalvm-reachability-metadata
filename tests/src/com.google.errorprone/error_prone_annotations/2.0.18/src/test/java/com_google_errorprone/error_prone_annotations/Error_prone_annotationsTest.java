/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_errorprone.error_prone_annotations;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CompatibleWith;
import com.google.errorprone.annotations.CompileTimeConstant;
import com.google.errorprone.annotations.DoNotCall;
import com.google.errorprone.annotations.DoNotMock;
import com.google.errorprone.annotations.ForOverride;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import com.google.errorprone.annotations.Immutable;
import com.google.errorprone.annotations.IncompatibleModifiers;
import com.google.errorprone.annotations.MustBeClosed;
import com.google.errorprone.annotations.NoAllocation;
import com.google.errorprone.annotations.RequiredModifiers;
import com.google.errorprone.annotations.RestrictedApi;
import com.google.errorprone.annotations.SuppressPackageLocation;
import com.google.errorprone.annotations.Var;
import com.google.errorprone.annotations.concurrent.LazyInit;
import com.google.errorprone.annotations.concurrent.LockMethod;
import com.google.errorprone.annotations.concurrent.UnlockMethod;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;
import javax.lang.model.element.Modifier;
import org.junit.jupiter.api.Test;

public class Error_prone_annotationsTest {
    @Test
    void allAnnotationTypesAreUsableAsPublicApiClassLiterals() {
        Class<?>[] annotationTypes = {
            CanIgnoreReturnValue.class,
            CompatibleWith.class,
            CompileTimeConstant.class,
            LazyInit.class,
            LockMethod.class,
            UnlockMethod.class,
            DoNotCall.class,
            DoNotMock.class,
            FormatMethod.class,
            FormatString.class,
            ForOverride.class,
            Immutable.class,
            IncompatibleModifiers.class,
            MustBeClosed.class,
            NoAllocation.class,
            RequiredModifiers.class,
            RestrictedApi.class,
            SuppressPackageLocation.class,
            Var.class
        };

        assertThat(annotationTypes)
                .containsExactly(
                        CanIgnoreReturnValue.class,
                        CompatibleWith.class,
                        CompileTimeConstant.class,
                        LazyInit.class,
                        LockMethod.class,
                        UnlockMethod.class,
                        DoNotCall.class,
                        DoNotMock.class,
                        FormatMethod.class,
                        FormatString.class,
                        ForOverride.class,
                        Immutable.class,
                        IncompatibleModifiers.class,
                        MustBeClosed.class,
                        NoAllocation.class,
                        RequiredModifiers.class,
                        RestrictedApi.class,
                        SuppressPackageLocation.class,
                        Var.class);
    }

    @Test
    void formattingAndCompileTimeValueAnnotationsDoNotChangeRuntimeResults() {
        String formatted = MessageFormats.format("count=%d name=%s", 7, "alpha");
        boolean compatible = MessageFormats.containsCandidate("alphabet", "alpha");
        String constant = MessageFormats.requireCompileTimeConstant("stable-token");

        assertThat(formatted).isEqualTo("count=7 name=alpha");
        assertThat(compatible).isTrue();
        assertThat(constant).isEqualTo("stable-token");
    }

    @Test
    void immutabilityOverrideAndDoNotMockAnnotationsCanDescribeDomainTypes() {
        StringBox box = new StringBox("one", "two", "three");
        DocumentedTemplate template = new ConcreteTemplate("verified");

        assertThat(box.first()).isEqualTo("one");
        assertThat(box.values()).containsExactly("one", "two", "three");
        assertThat(template.render()).isEqualTo("template:verified");
        assertThat(LegacyApi.supportedReplacement()).isEqualTo("replacement");
    }

    @Test
    void resourceAndRestrictedApiAnnotationsAllowNormalCalls() {
        List<String> events = new ArrayList<>();

        try (TrackedResource resource = TrackedResource.open(events)) {
            assertThat(resource.read()).isEqualTo("payload");
            assertThat(RestrictedOperations.create(events).perform()).isEqualTo("restricted-result");
        }

        assertThat(events).containsExactly("open", "create-restricted", "perform-restricted", "close");
    }

    @Test
    void concurrencyAnnotationsCanMarkLockingProtocol() {
        GuardedCounter counter = new GuardedCounter();

        counter.lock();
        try {
            counter.incrementWhileLocked();
            counter.incrementWhileLocked();
            assertThat(counter.currentWhileLocked()).isEqualTo(2);
        } finally {
            counter.unlock();
        }
    }

    @Test
    void variableAndLazyInitializationAnnotationsSupportOrdinaryStateChanges() {
        LazyCache cache = new LazyCache();

        assertThat(cache.uppercase("native")).isEqualTo("NATIVE");
        assertThat(cache.uppercase("ignored-after-initialization")).isEqualTo("NATIVE");
        assertThat(cache.replaceWithMutableLocal("updated")).isEqualTo("updated!");
    }

    @Test
    void modifierConstraintAnnotationsCanDefinePublicApiMarkers() {
        PublicApiEndpoint endpoint = new PublicApiEndpoint("stable");

        assertThat(endpoint.name()).isEqualTo("stable");
        assertThat(endpoint.supportedOperations()).containsExactly("read", "write");
    }

    @Test
    void canIgnoreReturnValueSupportsFluentMutatorsWhenResultIsNotUsed() {
        FluentEventLog log = new FluentEventLog();

        log.add("created");
        log.add("validated").add("committed");

        assertThat(log.events()).containsExactly("created", "validated", "committed");
    }

    @FormatMethod
    private static String annotatedFormat(@FormatString String pattern, Object... args) {
        return String.format(Locale.US, pattern, args);
    }

    @Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface AllowInternalUse {}

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface WarnInternalUse {}

    @RequiredModifiers(Modifier.PUBLIC)
    @IncompatibleModifiers({Modifier.PRIVATE, Modifier.PROTECTED})
    public @interface PublicApiMarker {}

    @DoNotMock("Use the concrete test implementation instead")
    public @interface ContractAnnotation {}

    @DoNotMock("Use RealDocumentedTemplate in tests")
    private interface DocumentedTemplate {
        String render();
    }

    @CanIgnoreReturnValue
    private static final class MessageFormats {
        private MessageFormats() { }

        @FormatMethod
        static String format(@FormatString String pattern, Object... args) {
            return annotatedFormat(pattern, args);
        }

        static boolean containsCandidate(String text, @CompatibleWith("T") Object candidate) {
            return text.contains(String.valueOf(candidate));
        }

        static String requireCompileTimeConstant(@CompileTimeConstant String token) {
            return token;
        }
    }

    @Immutable(containerOf = "T")
    private static class ImmutableListHolder<T> {
        private final List<T> values;

        private ImmutableListHolder(List<T> values) {
            this.values = List.copyOf(values);
        }

        final List<T> values() {
            return values;
        }
    }

    private static final class StringBox extends ImmutableListHolder<String> {
        private StringBox(String first, String second, String third) {
            super(List.of(first, second, third));
        }

        private String first() {
            return values().get(0);
        }
    }

    private abstract static class TemplateBase implements DocumentedTemplate {
        @Override
        public final String render() {
            return "template:" + valueForTemplate();
        }

        @ForOverride
        protected abstract String valueForTemplate();
    }

    private static final class ConcreteTemplate extends TemplateBase {
        private final String value;

        private ConcreteTemplate(String value) {
            this.value = value;
        }

        @Override
        protected String valueForTemplate() {
            return value;
        }
    }

    private static final class TrackedResource implements AutoCloseable {
        private final List<String> events;
        private boolean closed;

        @MustBeClosed
        private TrackedResource(List<String> events) {
            this.events = events;
            events.add("open");
        }

        @MustBeClosed
        static TrackedResource open(List<String> events) {
            return new TrackedResource(events);
        }

        String read() {
            assertThat(closed).isFalse();
            return "payload";
        }

        @Override
        public void close() {
            closed = true;
            events.add("close");
        }
    }

    private static final class RestrictedOperations {
        private final List<String> events;

        @RestrictedApi(
                explanation = "Only the allow-listed test fixture may create this operation.",
                link = "https://example.invalid/restricted-create",
                allowedOnPath = ".*/com_google_errorprone/.*",
                whitelistAnnotations = AllowInternalUse.class,
                whitelistWithWarningAnnotations = WarnInternalUse.class)
        private RestrictedOperations(List<String> events) {
            this.events = events;
            events.add("create-restricted");
        }

        @AllowInternalUse
        static RestrictedOperations create(List<String> events) {
            return new RestrictedOperations(events);
        }

        @RestrictedApi(
                checkerName = "RestrictedOperationChecker",
                explanation = "Operation is intentionally limited to the annotated fixture.",
                link = "https://example.invalid/restricted-perform",
                whitelistAnnotations = AllowInternalUse.class,
                whitelistWithWarningAnnotations = WarnInternalUse.class)
        @CanIgnoreReturnValue
        String perform() {
            events.add("perform-restricted");
            return "restricted-result";
        }
    }

    private static final class GuardedCounter {
        private final ReentrantLock lock = new ReentrantLock();
        private int count;

        @LockMethod("lock")
        void lock() {
            lock.lock();
        }

        @UnlockMethod("lock")
        void unlock() {
            lock.unlock();
        }

        void incrementWhileLocked() {
            assertThat(lock.isHeldByCurrentThread()).isTrue();
            count++;
        }

        @NoAllocation
        int currentWhileLocked() {
            assertThat(lock.isHeldByCurrentThread()).isTrue();
            return count;
        }
    }

    private static final class LazyCache {
        @LazyInit private String cached;
        @Var private String mutableField = "initial";

        String uppercase(@Var String input) {
            if (cached == null) {
                cached = input.toUpperCase(Locale.US);
            }
            return cached;
        }

        String replaceWithMutableLocal(String value) {
            @Var String local = value;
            mutableField = local + "!";
            local = mutableField;
            return local;
        }
    }

    private static final class FluentEventLog {
        private final List<String> events = new ArrayList<>();

        @CanIgnoreReturnValue
        FluentEventLog add(String event) {
            events.add(event);
            return this;
        }

        List<String> events() {
            return List.copyOf(events);
        }
    }

    private static final class LegacyApi {
        private LegacyApi() { }

        @DoNotCall("Use supportedReplacement instead")
        static String removedOperation() {
            return "legacy";
        }

        static String supportedReplacement() {
            return "replacement";
        }
    }

    private static final class PublicApiEndpoint {
        private final String name;

        private PublicApiEndpoint(String name) {
            this.name = name;
        }

        @PublicApiMarker
        public String name() {
            return name;
        }

        @PublicApiMarker
        public List<String> supportedOperations() {
            return List.of("read", "write");
        }
    }
}
