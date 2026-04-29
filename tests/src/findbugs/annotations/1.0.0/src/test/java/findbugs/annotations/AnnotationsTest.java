/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package findbugs.annotations;

import java.lang.annotation.Annotation;
import java.util.Locale;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.CheckReturnValue;
import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.DefaultAnnotationForFields;
import edu.umd.cs.findbugs.annotations.DefaultAnnotationForMethods;
import edu.umd.cs.findbugs.annotations.DefaultAnnotationForParameters;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.OverrideMustInvoke;
import edu.umd.cs.findbugs.annotations.Priority;
import edu.umd.cs.findbugs.annotations.UnknownNullness;
import edu.umd.cs.findbugs.annotations.When;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationsTest {
    @Test
    void priorityAndOverrideTimingEnumsExposeStableOrderingAndLookups() {
        assertThat(Priority.values()).containsExactly(
                Priority.HIGH,
                Priority.MEDIUM,
                Priority.LOW,
                Priority.IGNORE);
        assertThat(Priority.valueOf("HIGH")).isSameAs(Priority.HIGH);
        assertThat(Priority.valueOf("MEDIUM")).isSameAs(Priority.MEDIUM);
        assertThat(Priority.valueOf("LOW")).isSameAs(Priority.LOW);
        assertThat(Priority.valueOf("IGNORE")).isSameAs(Priority.IGNORE);
        assertThat(Priority.HIGH.getPriorityValue()).isEqualTo(1);
        assertThat(Priority.MEDIUM.getPriorityValue()).isEqualTo(2);
        assertThat(Priority.LOW.getPriorityValue()).isEqualTo(3);
        assertThat(Priority.IGNORE.getPriorityValue()).isEqualTo(5);

        assertThat(When.values()).containsExactly(When.FIRST, When.ANYTIME, When.LAST);
        assertThat(When.valueOf("FIRST")).isSameAs(When.FIRST);
        assertThat(When.valueOf("ANYTIME")).isSameAs(When.ANYTIME);
        assertThat(When.valueOf("LAST")).isSameAs(When.LAST);
    }

    @Test
    void markerAnnotationInterfacesExposeTheirAnnotationTypes() {
        CheckForNull checkForNull = checkForNull();
        NonNull nonNull = nonNull();
        Nullable nullable = nullable();
        UnknownNullness unknownNullness = unknownNullness();
        net.jcip.annotations.Immutable immutable = immutable();
        net.jcip.annotations.NotThreadSafe notThreadSafe = notThreadSafe();
        net.jcip.annotations.ThreadSafe threadSafe = threadSafe();

        assertThat(checkForNull.annotationType()).isSameAs(CheckForNull.class);
        assertThat(nonNull.annotationType()).isSameAs(NonNull.class);
        assertThat(nullable.annotationType()).isSameAs(Nullable.class);
        assertThat(unknownNullness.annotationType()).isSameAs(UnknownNullness.class);
        assertThat(immutable.annotationType()).isSameAs(net.jcip.annotations.Immutable.class);
        assertThat(notThreadSafe.annotationType()).isSameAs(net.jcip.annotations.NotThreadSafe.class);
        assertThat(threadSafe.annotationType()).isSameAs(net.jcip.annotations.ThreadSafe.class);
    }

    @Test
    void configuredAnnotationInterfacesExposeValuesAndPriorities() {
        CheckReturnValue highValue = checkReturnValue(Priority.HIGH, "callers must use the opened handle");
        CheckReturnValue ignoredValue = checkReturnValue(Priority.IGNORE, "explicitly documented fire-and-forget path");
        OverrideMustInvoke first = overrideMustInvoke(When.FIRST);
        OverrideMustInvoke anytime = overrideMustInvoke(When.ANYTIME);
        OverrideMustInvoke last = overrideMustInvoke(When.LAST);
        edu.umd.cs.findbugs.annotations.SuppressWarnings suppressWarnings = suppressWarnings(
                "Covered by integration test fixture",
                "NP_NONNULL_RETURN_VIOLATION",
                "RV_RETURN_VALUE_IGNORED");
        net.jcip.annotations.GuardedBy guardedBy = guardedBy("stateLock");

        assertThat(highValue.priority()).isSameAs(Priority.HIGH);
        assertThat(highValue.explanation()).isEqualTo("callers must use the opened handle");
        assertThat(highValue.annotationType()).isSameAs(CheckReturnValue.class);
        assertThat(ignoredValue.priority()).isSameAs(Priority.IGNORE);
        assertThat(ignoredValue.explanation()).isEqualTo("explicitly documented fire-and-forget path");

        assertThat(first.value()).isSameAs(When.FIRST);
        assertThat(first.annotationType()).isSameAs(OverrideMustInvoke.class);
        assertThat(anytime.value()).isSameAs(When.ANYTIME);
        assertThat(last.value()).isSameAs(When.LAST);

        assertThat(suppressWarnings.value()).containsExactly("NP_NONNULL_RETURN_VIOLATION", "RV_RETURN_VALUE_IGNORED");
        assertThat(suppressWarnings.justification()).isEqualTo("Covered by integration test fixture");
        assertThat(suppressWarnings.annotationType()).isSameAs(edu.umd.cs.findbugs.annotations.SuppressWarnings.class);

        assertThat(guardedBy.value()).isEqualTo("stateLock");
        assertThat(guardedBy.annotationType()).isSameAs(net.jcip.annotations.GuardedBy.class);
    }

    @Test
    void defaultAnnotationInterfacesPreserveConfiguredScopes() {
        DefaultAnnotation defaultAnnotation = defaultAnnotation(
                Priority.MEDIUM,
                annotationTypes(NonNull.class, CheckReturnValue.class));
        DefaultAnnotationForFields defaultAnnotationForFields = defaultAnnotationForFields(
                Priority.HIGH,
                annotationTypes(NonNull.class, CheckForNull.class));
        DefaultAnnotationForMethods defaultAnnotationForMethods = defaultAnnotationForMethods(
                Priority.LOW,
                annotationTypes(CheckReturnValue.class, UnknownNullness.class));
        DefaultAnnotationForParameters defaultAnnotationForParameters = defaultAnnotationForParameters(
                Priority.IGNORE,
                annotationTypes(Nullable.class, CheckForNull.class));

        assertThat(defaultAnnotation.value()).containsExactly(NonNull.class, CheckReturnValue.class);
        assertThat(defaultAnnotation.priority()).isSameAs(Priority.MEDIUM);
        assertThat(defaultAnnotation.annotationType()).isSameAs(DefaultAnnotation.class);

        assertThat(defaultAnnotationForFields.value()).containsExactly(NonNull.class, CheckForNull.class);
        assertThat(defaultAnnotationForFields.priority()).isSameAs(Priority.HIGH);
        assertThat(defaultAnnotationForFields.annotationType()).isSameAs(DefaultAnnotationForFields.class);

        assertThat(defaultAnnotationForMethods.value()).containsExactly(CheckReturnValue.class, UnknownNullness.class);
        assertThat(defaultAnnotationForMethods.priority()).isSameAs(Priority.LOW);
        assertThat(defaultAnnotationForMethods.priority().getPriorityValue()).isEqualTo(3);
        assertThat(defaultAnnotationForMethods.annotationType()).isSameAs(DefaultAnnotationForMethods.class);

        assertThat(defaultAnnotationForParameters.value()).containsExactly(Nullable.class, CheckForNull.class);
        assertThat(defaultAnnotationForParameters.priority()).isSameAs(Priority.IGNORE);
        assertThat(defaultAnnotationForParameters.annotationType()).isSameAs(DefaultAnnotationForParameters.class);
    }

    @Test
    void annotatedFixturesRemainUsableInRegularCodePaths() {
        ManagedResource parent = new ManagedResource("  Parent Resource  ", null);
        ManagedResource child = parent.openChild("  Child Resource  ");

        assertThat(parent.canonicalName()).isEqualTo("parent resource");
        assertThat(parent.aliasOrNull(false)).isNull();
        assertThat(parent.aliasOrNull(true)).isEqualTo("parent resource-alias");
        assertThat(parent.displayName("  PRIMARY OWNER  ")).isEqualTo("primary owner");
        assertThat(parent.displayName("   ")).isEqualTo("parent resource");
        assertThat(child.canonicalName()).isEqualTo("parent resource/child resource");
        assertThat(parent.openedChildren()).isEqualTo(1);
        assertThat(parent.close("  done  ")).isEqualTo("parent resource:done");
        assertThat(parent.isClosed()).isTrue();

        GuardedCounter counter = new GuardedCounter();
        assertThat(counter.incrementAndGet()).isEqualTo(1);
        assertThat(counter.incrementAndGet()).isEqualTo(2);
        assertThat(counter.current()).isEqualTo(2);

        ImmutableCoordinate coordinate = new ImmutableCoordinate(" findbugs ", " annotations ");
        assertThat(coordinate.coordinate()).isEqualTo("findbugs:annotations");

        MutableNote mutableNote = new MutableNote("native");
        mutableNote.append(" image");
        mutableNote.append(" annotations");
        assertThat(mutableNote.value()).isEqualTo("native image annotations");

        LifecycleOverride lifecycle = new LifecycleOverride("  library  ");
        assertThat(lifecycle.finish("  verified  ")).isEqualTo("library:verified:closed");
        assertThat(lifecycle.isClosed()).isTrue();
    }

    @Test
    void checkReturnValueContractCanBeDeclaredOnBaseFactoriesAndUsedThroughOverrides() {
        PlanFactory factory = new NumberedPlanFactory("  Metadata  ");
        BuildPlan firstPlan = factory.createPlan("  Native Image  ");
        assertThat(firstPlan.coordinate()).isEqualTo("metadata:native image:1");

        NumberedPlanFactory concreteFactory = new NumberedPlanFactory("  Reachability  ");
        BuildPlan secondPlan = concreteFactory.createPlan("  Agent Config  ");
        BuildPlan thirdPlan = concreteFactory.createPlan("  Test Runtime  ");

        assertThat(secondPlan.coordinate()).isEqualTo("reachability:agent config:1");
        assertThat(thirdPlan.coordinate()).isEqualTo("reachability:test runtime:2");
        assertThat(concreteFactory.issuedPlans()).isEqualTo(2);
    }

    @Test
    void overrideMustInvokeFirstSupportsBaseInitializationBeforeSubclassWork() {
        SpecializedStartup startup = new SpecializedStartup("  Native Image  ");

        assertThat(startup.start("  Metadata  ")).isEqualTo("native image:metadata:base:specialized");
        assertThat(startup.trace()).isEqualTo("base->specialized");
        assertThat(startup.isStarted()).isTrue();
    }

    private static CheckForNull checkForNull() {
        return new CheckForNull() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return CheckForNull.class;
            }
        };
    }

    private static NonNull nonNull() {
        return new NonNull() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return NonNull.class;
            }
        };
    }

    private static Nullable nullable() {
        return new Nullable() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return Nullable.class;
            }
        };
    }

    private static UnknownNullness unknownNullness() {
        return new UnknownNullness() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return UnknownNullness.class;
            }
        };
    }

    private static net.jcip.annotations.Immutable immutable() {
        return new net.jcip.annotations.Immutable() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return net.jcip.annotations.Immutable.class;
            }
        };
    }

    private static net.jcip.annotations.NotThreadSafe notThreadSafe() {
        return new net.jcip.annotations.NotThreadSafe() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return net.jcip.annotations.NotThreadSafe.class;
            }
        };
    }

    private static net.jcip.annotations.ThreadSafe threadSafe() {
        return new net.jcip.annotations.ThreadSafe() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return net.jcip.annotations.ThreadSafe.class;
            }
        };
    }

    private static CheckReturnValue checkReturnValue(Priority priority, String explanation) {
        return new CheckReturnValue() {
            @Override
            public Priority priority() {
                return priority;
            }

            @Override
            public String explanation() {
                return explanation;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return CheckReturnValue.class;
            }
        };
    }

    private static OverrideMustInvoke overrideMustInvoke(When value) {
        return new OverrideMustInvoke() {
            @Override
            public When value() {
                return value;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return OverrideMustInvoke.class;
            }
        };
    }

    private static edu.umd.cs.findbugs.annotations.SuppressWarnings suppressWarnings(
            String justification,
            String... value) {
        return new edu.umd.cs.findbugs.annotations.SuppressWarnings() {
            @Override
            public String[] value() {
                return value;
            }

            @Override
            public String justification() {
                return justification;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return edu.umd.cs.findbugs.annotations.SuppressWarnings.class;
            }
        };
    }

    private static net.jcip.annotations.GuardedBy guardedBy(String value) {
        return new net.jcip.annotations.GuardedBy() {
            @Override
            public String value() {
                return value;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return net.jcip.annotations.GuardedBy.class;
            }
        };
    }

    @SafeVarargs
    private static Class<? extends Annotation>[] annotationTypes(Class<? extends Annotation>... value) {
        return value;
    }

    private static DefaultAnnotation defaultAnnotation(Priority priority, Class<? extends Annotation>[] value) {
        return new DefaultAnnotation() {
            @Override
            public Class<? extends Annotation>[] value() {
                return value;
            }

            @Override
            public Priority priority() {
                return priority;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return DefaultAnnotation.class;
            }
        };
    }

    private static DefaultAnnotationForFields defaultAnnotationForFields(
            Priority priority,
            Class<? extends Annotation>[] value) {
        return new DefaultAnnotationForFields() {
            @Override
            public Class<? extends Annotation>[] value() {
                return value;
            }

            @Override
            public Priority priority() {
                return priority;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return DefaultAnnotationForFields.class;
            }
        };
    }

    private static DefaultAnnotationForMethods defaultAnnotationForMethods(
            Priority priority,
            Class<? extends Annotation>[] value) {
        return new DefaultAnnotationForMethods() {
            @Override
            public Class<? extends Annotation>[] value() {
                return value;
            }

            @Override
            public Priority priority() {
                return priority;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return DefaultAnnotationForMethods.class;
            }
        };
    }

    private static DefaultAnnotationForParameters defaultAnnotationForParameters(
            Priority priority,
            Class<? extends Annotation>[] value) {
        return new DefaultAnnotationForParameters() {
            @Override
            public Class<? extends Annotation>[] value() {
                return value;
            }

            @Override
            public Priority priority() {
                return priority;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return DefaultAnnotationForParameters.class;
            }
        };
    }

    @DefaultAnnotation(NonNull.class)
    @DefaultAnnotationForFields(value = Nullable.class, priority = Priority.HIGH)
    @DefaultAnnotationForMethods(value = CheckReturnValue.class, priority = Priority.MEDIUM)
    @DefaultAnnotationForParameters(value = NonNull.class, priority = Priority.LOW)
    @net.jcip.annotations.ThreadSafe
    private static final class ManagedResource {
        private final Object stateLock = new Object();

        @NonNull
        private final String name;

        @CheckForNull
        private final String alias;

        @net.jcip.annotations.GuardedBy("stateLock")
        private int openedChildren;

        private boolean closed;

        @CheckReturnValue(priority = Priority.HIGH, explanation = "resource identity is required by callers")
        private ManagedResource(String name, @Nullable String alias) {
            this.name = normalize(name);
            this.alias = normalizeNullable(alias);
        }

        @CheckReturnValue(priority = Priority.HIGH, explanation = "the returned child resource must be closed")
        private ManagedResource openChild(String childName) {
            synchronized (stateLock) {
                openedChildren++;
            }
            return new ManagedResource(name + "/" + normalize(childName), alias);
        }

        @NonNull
        private String canonicalName() {
            @NonNull String localName = name;
            return localName;
        }

        @CheckForNull
        private String aliasOrNull(boolean includeAlias) {
            if (includeAlias && alias != null) {
                return alias;
            }
            return includeAlias ? name + "-alias" : null;
        }

        @NonNull
        @edu.umd.cs.findbugs.annotations.SuppressWarnings(
                value = "NP_NONNULL_RETURN_VIOLATION",
                justification = "The nullable fallback is handled explicitly")
        private String displayName(@UnknownNullness String preferredLabel) {
            @CheckForNull String normalizedLabel = normalizeNullable(preferredLabel);
            if (normalizedLabel != null) {
                return normalizedLabel;
            }
            if (alias != null) {
                return alias;
            }
            return name;
        }

        private String close(@NonNull String reason) {
            closed = true;
            return name + ":" + normalize(reason);
        }

        @net.jcip.annotations.GuardedBy("stateLock")
        private int openedChildren() {
            synchronized (stateLock) {
                return openedChildren;
            }
        }

        private boolean isClosed() {
            return closed;
        }
    }

    @net.jcip.annotations.ThreadSafe
    private static final class GuardedCounter {
        private final Object lock = new Object();

        @net.jcip.annotations.GuardedBy("lock")
        private int value;

        private int incrementAndGet() {
            synchronized (lock) {
                value++;
                return value;
            }
        }

        @net.jcip.annotations.GuardedBy("lock")
        private int current() {
            synchronized (lock) {
                return value;
            }
        }
    }

    @net.jcip.annotations.Immutable
    private static final class ImmutableCoordinate {
        private final String groupId;
        private final String artifactId;

        private ImmutableCoordinate(String groupId, String artifactId) {
            this.groupId = normalize(groupId);
            this.artifactId = normalize(artifactId);
        }

        private String coordinate() {
            return groupId + ":" + artifactId;
        }
    }

    @net.jcip.annotations.NotThreadSafe
    private static final class MutableNote {
        private final StringBuilder value;

        private MutableNote(String initialValue) {
            this.value = new StringBuilder(normalize(initialValue));
        }

        private void append(String fragment) {
            value.append(' ').append(normalize(fragment));
        }

        private String value() {
            return value.toString();
        }
    }

    private static class LifecycleBase {
        private final String name;
        private boolean closed;

        private LifecycleBase(String name) {
            this.name = normalize(name);
        }

        @OverrideMustInvoke(When.LAST)
        String finish(String reason) {
            closed = true;
            return name + ":" + normalize(reason) + ":closed";
        }

        boolean isClosed() {
            return closed;
        }
    }

    private static final class LifecycleOverride extends LifecycleBase {
        private LifecycleOverride(String name) {
            super(name);
        }

        @Override
        String finish(String reason) {
            return super.finish(reason);
        }
    }

    private abstract static class PlanFactory {
        private final String group;

        private PlanFactory(String group) {
            this.group = normalize(group);
        }

        @CheckReturnValue(priority = Priority.HIGH, explanation = "callers must use the generated plan")
        abstract BuildPlan createPlan(String taskName);

        final String group() {
            return group;
        }
    }

    private static final class NumberedPlanFactory extends PlanFactory {
        private int issuedPlans;

        private NumberedPlanFactory(String group) {
            super(group);
        }

        @Override
        BuildPlan createPlan(String taskName) {
            issuedPlans++;
            return new BuildPlan(group(), normalize(taskName), issuedPlans);
        }

        private int issuedPlans() {
            return issuedPlans;
        }
    }

    private static final class BuildPlan {
        private final String group;
        private final String taskName;
        private final int sequence;

        private BuildPlan(String group, String taskName, int sequence) {
            this.group = group;
            this.taskName = taskName;
            this.sequence = sequence;
        }

        private String coordinate() {
            return group + ":" + taskName + ":" + sequence;
        }
    }

    private static class StartupBase {
        private final String name;
        private final StringBuilder trace = new StringBuilder();
        private boolean started;

        private StartupBase(String name) {
            this.name = normalize(name);
        }

        @OverrideMustInvoke(When.FIRST)
        String start(String detail) {
            started = true;
            appendTrace("base");
            return name + ":" + normalize(detail) + ":base";
        }

        final void appendTrace(String step) {
            if (trace.length() > 0) {
                trace.append("->");
            }
            trace.append(step);
        }

        final String trace() {
            return trace.toString();
        }

        final boolean isStarted() {
            return started;
        }
    }

    private static final class SpecializedStartup extends StartupBase {
        private SpecializedStartup(String name) {
            super(name);
        }

        @Override
        String start(String detail) {
            String initialized = super.start(detail);
            appendTrace("specialized");
            return initialized + ":specialized";
        }
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = normalize(value);
        return normalized.isEmpty() ? null : normalized;
    }
}
