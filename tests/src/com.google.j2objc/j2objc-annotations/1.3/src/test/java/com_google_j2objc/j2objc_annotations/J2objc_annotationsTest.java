/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_j2objc.j2objc_annotations;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

import com.google.j2objc.annotations.AutoreleasePool;
import com.google.j2objc.annotations.J2ObjCIncompatible;
import com.google.j2objc.annotations.LoopTranslation;
import com.google.j2objc.annotations.ObjectiveCName;
import com.google.j2objc.annotations.Property;
import com.google.j2objc.annotations.ReflectionSupport;
import com.google.j2objc.annotations.RetainedLocalRef;
import com.google.j2objc.annotations.RetainedWith;
import com.google.j2objc.annotations.Weak;
import com.google.j2objc.annotations.WeakOuter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class J2objc_annotationsTest {
    @Test
    void annotatedTypesRemainUsableInRegularJavaCode() {
        AnnotatedBridge annotatedBridge = new AnnotatedBridge("primary", "nativeAlias");
        LoopDrivenCollector iteratorCollector = new LoopDrivenCollector();
        LoopDrivenCollector fastEnumerationCollector = new LoopDrivenCollector();

        assertThat(annotatedBridge.describe()).isEqualTo("primary:nativeAlias");
        assertThat(iteratorCollector.collectWith(LoopTranslation.LoopStyle.JAVA_ITERATOR, List.of("a", "b", "c")))
                .isEqualTo("iterator:a|b|c");
        assertThat(fastEnumerationCollector.collectWith(LoopTranslation.LoopStyle.FAST_ENUMERATION, List.of("a", "b", "c")))
                .isEqualTo("fast:[a][b][c]");
        assertThat(annotatedBridge.rename("renamedAlias")).isEqualTo("primary:renamedAlias");
        assertThat(annotatedBridge.aliasHistory()).containsExactly("nativeAlias", "renamedAlias");
        assertThat(annotatedBridge.reflectionMode()).isEqualTo("FULL");
    }

    @Test
    void propertyAnnotationSupportsDefaultAttributeValue() {
        DefaultPropertyHolder defaultPropertyHolder = new DefaultPropertyHolder("displayName");

        assertThat(defaultPropertyHolder.displayName()).isEqualTo("displayName");
    }

    @Test
    void weakOuterAnnotatedInnerTypesCanTransformOuterOwnedState() {
        InnerRendererOwner innerRendererOwner = new InnerRendererOwner("ios");
        InnerRendererOwner.InnerAliasRenderer innerAliasRenderer = innerRendererOwner.new InnerAliasRenderer();

        assertThat(innerAliasRenderer.render(List.of(" view ", "controller", "adapter ")))
                .isEqualTo("ios::VIEW|CONTROLLER|ADAPTER");
        assertThat(innerRendererOwner.renderedAliases()).containsExactly("VIEW", "CONTROLLER", "ADAPTER");
    }

    @Test
    void manualAnnotationImplementationsExposeConfiguredMembers() {
        ObjectiveCName objectiveCName = new ObjectiveCName() {
            @Override
            public String value() {
                return "BridgeType";
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return ObjectiveCName.class;
            }
        };
        Property property = new Property() {
            @Override
            public String value() {
                return "copy, nonatomic";
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return Property.class;
            }
        };
        LoopTranslation loopTranslation = new LoopTranslation() {
            @Override
            public LoopStyle value() {
                return LoopStyle.FAST_ENUMERATION;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return LoopTranslation.class;
            }
        };
        ReflectionSupport reflectionSupport = new ReflectionSupport() {
            @Override
            public Level value() {
                return Level.NATIVE_ONLY;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return ReflectionSupport.class;
            }
        };

        assertThat(objectiveCName.value()).isEqualTo("BridgeType");
        assertThat(objectiveCName.annotationType()).isSameAs(ObjectiveCName.class);
        assertThat(property.value()).isEqualTo("copy, nonatomic");
        assertThat(property.annotationType()).isSameAs(Property.class);
        assertThat(loopTranslation.value()).isSameAs(LoopTranslation.LoopStyle.FAST_ENUMERATION);
        assertThat(loopTranslation.annotationType()).isSameAs(LoopTranslation.class);
        assertThat(reflectionSupport.value()).isSameAs(ReflectionSupport.Level.NATIVE_ONLY);
        assertThat(reflectionSupport.annotationType()).isSameAs(ReflectionSupport.class);
    }

    @Test
    void markerAnnotationsCanBeImplementedWithoutReflection() {
        AutoreleasePool autoreleasePool = new AutoreleasePool() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return AutoreleasePool.class;
            }
        };
        J2ObjCIncompatible j2ObjCIncompatible = new J2ObjCIncompatible() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return J2ObjCIncompatible.class;
            }
        };
        RetainedLocalRef retainedLocalRef = new RetainedLocalRef() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return RetainedLocalRef.class;
            }
        };
        RetainedWith retainedWith = new RetainedWith() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return RetainedWith.class;
            }
        };
        Weak weak = new Weak() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return Weak.class;
            }
        };
        WeakOuter weakOuter = new WeakOuter() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return WeakOuter.class;
            }
        };

        assertThat(List.of(
                autoreleasePool.annotationType(),
                j2ObjCIncompatible.annotationType(),
                retainedLocalRef.annotationType(),
                retainedWith.annotationType(),
                weak.annotationType(),
                weakOuter.annotationType()))
                .containsExactly(
                        AutoreleasePool.class,
                        J2ObjCIncompatible.class,
                        RetainedLocalRef.class,
                        RetainedWith.class,
                        Weak.class,
                        WeakOuter.class);
    }

    @Test
    void nestedEnumsProvideStableLookupAndOrdering() {
        assertThat(LoopTranslation.LoopStyle.values())
                .containsExactly(LoopTranslation.LoopStyle.JAVA_ITERATOR, LoopTranslation.LoopStyle.FAST_ENUMERATION);
        assertThat(LoopTranslation.LoopStyle.valueOf("JAVA_ITERATOR")).isSameAs(LoopTranslation.LoopStyle.JAVA_ITERATOR);
        assertThat(LoopTranslation.LoopStyle.valueOf("FAST_ENUMERATION"))
                .isSameAs(LoopTranslation.LoopStyle.FAST_ENUMERATION);
        assertThat(ReflectionSupport.Level.values())
                .containsExactly(ReflectionSupport.Level.NATIVE_ONLY, ReflectionSupport.Level.FULL);
        assertThat(ReflectionSupport.Level.valueOf("NATIVE_ONLY")).isSameAs(ReflectionSupport.Level.NATIVE_ONLY);
        assertThat(ReflectionSupport.Level.valueOf("FULL")).isSameAs(ReflectionSupport.Level.FULL);
    }

    @J2ObjCIncompatible
    @interface UnsupportedBridgeContract {
    }

    @WeakOuter
    @ReflectionSupport(ReflectionSupport.Level.FULL)
    @ObjectiveCName("BridgeType")
    static final class AnnotatedBridge {
        @Property("copy, nonatomic")
        private String alias;

        @RetainedWith
        private final AliasHistory aliasHistory;

        @Weak
        @J2ObjCIncompatible
        private String legacyAlias;

        @J2ObjCIncompatible
        @ObjectiveCName("initWithAlias")
        AnnotatedBridge(String legacyAlias, String alias) {
            this.alias = alias;
            this.aliasHistory = new AliasHistory();
            this.legacyAlias = legacyAlias;
            this.aliasHistory.record(alias);
        }

        @ObjectiveCName("describeBridge")
        String describe() {
            return legacyAlias + ":" + alias;
        }

        @J2ObjCIncompatible
        @ObjectiveCName("renameTo")
        String rename(@Weak String updatedAlias) {
            @Weak
            String pendingAlias = updatedAlias.trim();
            this.alias = pendingAlias;
            this.aliasHistory.record(pendingAlias);
            return describe();
        }

        String reflectionMode() {
            return ReflectionSupport.Level.FULL.name();
        }

        List<String> aliasHistory() {
            return aliasHistory.snapshot();
        }
    }

    static final class DefaultPropertyHolder {
        @Property
        private final String displayName;

        DefaultPropertyHolder(String displayName) {
            this.displayName = displayName;
        }

        String displayName() {
            return displayName;
        }
    }

    static final class AliasHistory {
        private final List<String> values = new ArrayList<>();

        void record(String value) {
            values.add(value);
        }

        List<String> snapshot() {
            return List.copyOf(values);
        }
    }

    static final class InnerRendererOwner {
        private final String namespace;
        private final List<String> renderedAliases = new ArrayList<>();

        InnerRendererOwner(String namespace) {
            this.namespace = namespace;
        }

        List<String> renderedAliases() {
            return List.copyOf(renderedAliases);
        }

        @WeakOuter
        final class InnerAliasRenderer {
            String render(List<String> aliases) {
                @AutoreleasePool
                StringBuilder renderedValue = new StringBuilder(namespace).append("::");

                for (String alias : aliases) {
                    String normalizedAlias = alias.trim().toUpperCase();
                    renderedAliases.add(normalizedAlias);
                    if (renderedValue.length() > namespace.length() + 2) {
                        renderedValue.append('|');
                    }
                    renderedValue.append(normalizedAlias);
                }
                return renderedValue.toString();
            }
        }
    }

    static final class LoopDrivenCollector {
        @AutoreleasePool
        String collectWith(LoopTranslation.LoopStyle loopStyle, List<String> input) {
            @RetainedLocalRef
            List<String> collectedValues = new ArrayList<>();
            @LoopTranslation(LoopTranslation.LoopStyle.JAVA_ITERATOR)
            int itemCount = input.size();

            for (int index = 0; index < itemCount; index++) {
                String value = input.get(index);
                if (loopStyle == LoopTranslation.LoopStyle.JAVA_ITERATOR) {
                    collectedValues.add(value);
                } else {
                    collectedValues.add("[" + value + "]");
                }
            }

            if (loopStyle == LoopTranslation.LoopStyle.JAVA_ITERATOR) {
                return "iterator:" + String.join("|", collectedValues);
            }
            return "fast:" + String.join("", collectedValues);
        }
    }
}
