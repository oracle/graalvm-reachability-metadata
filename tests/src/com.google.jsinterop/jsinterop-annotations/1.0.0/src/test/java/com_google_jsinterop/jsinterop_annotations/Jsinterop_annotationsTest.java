/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_jsinterop.jsinterop_annotations;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import jsinterop.annotations.JsConstructor;
import jsinterop.annotations.JsFunction;
import jsinterop.annotations.JsIgnore;
import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsOptional;
import jsinterop.annotations.JsOverlay;
import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsProperty;
import jsinterop.annotations.JsType;
import org.junit.jupiter.api.Test;

public class Jsinterop_annotationsTest {
    @Test
    void annotatedJvmTypesKeepTheirOrdinaryRuntimeBehavior() {
        ExportedWidget widget = new ExportedWidget("alpha", "internal-1");

        assertThat(widget.getTitle()).isEqualTo("alpha");
        assertThat(widget.rename("beta")).isEqualTo("alpha->beta");
        assertThat(widget.getTitle()).isEqualTo("beta");
        assertThat(widget.decoratedTitle()).isEqualTo("Widget[beta]");
        assertThat(widget.internalId()).isEqualTo("internal-1");
        assertThat(ExportedWidget.DEFAULT_TITLE).isEqualTo("untitled");
    }

    @Test
    void jsTypeInterfaceCanCombineMethodPropertyOverlayAndOptionalAnnotations() {
        CounterAdapter counter = new CounterAdapter(7);

        assertThat(counter.getCount()).isEqualTo(7);
        assertThat(counter.increment(null)).isEqualTo(8);
        assertThat(counter.increment(4)).isEqualTo(12);
        assertThat(counter.incrementOnce()).isEqualTo(13);

        counter.setCount(30);
        assertThat(counter.getCount()).isEqualTo(30);
    }

    @Test
    void jsFunctionInterfacesWorkWithLambdasAndMethodReferences() {
        ValueMapper doubler = value -> value * 2;
        ValueMapper absolute = Math::abs;

        assertThat(applyMapping(9, doubler)).isEqualTo(18);
        assertThat(applyMapping(-5, absolute)).isEqualTo(5);
    }

    @Test
    void autoNamedAnnotationsCanOmitOptionalMembers() {
        AutoNamedCounter counter = new AutoNamedCounter(3);

        assertThat(counter.currentValue()).isEqualTo(3);
        counter.setCurrentValue(6);
        assertThat(counter.multiply(7)).isEqualTo(42);
        assertThat(counter.summary()).isEqualTo("value=6");
    }

    @Test
    void annotationInterfacesExposeTheirPublicMembers() {
        JsType jsType = new JsTypeLiteral("Widget", "example.widgets", true);
        JsMethod jsMethod = new JsMethodLiteral("calculate", "example.methods");
        JsProperty jsProperty = new JsPropertyLiteral("enabled", JsPackage.GLOBAL);
        JsPackage jsPackage = new JsPackageLiteral("example.package");

        assertThat(jsType.name()).isEqualTo("Widget");
        assertThat(jsType.namespace()).isEqualTo("example.widgets");
        assertThat(jsType.isNative()).isTrue();
        assertThat(jsType.annotationType()).isSameAs(JsType.class);

        assertThat(jsMethod.name()).isEqualTo("calculate");
        assertThat(jsMethod.namespace()).isEqualTo("example.methods");
        assertThat(jsMethod.annotationType()).isSameAs(JsMethod.class);

        assertThat(jsProperty.name()).isEqualTo("enabled");
        assertThat(jsProperty.namespace()).isEqualTo(JsPackage.GLOBAL);
        assertThat(jsProperty.annotationType()).isSameAs(JsProperty.class);

        assertThat(jsPackage.namespace()).isEqualTo("example.package");
        assertThat(jsPackage.annotationType()).isSameAs(JsPackage.class);
        assertThat(JsPackage.GLOBAL).isEqualTo("<global>");
    }

    @Test
    void markerAnnotationsIdentifyTheirAnnotationTypes() {
        assertThat(new JsConstructorLiteral().annotationType()).isSameAs(JsConstructor.class);
        assertThat(new JsFunctionLiteral().annotationType()).isSameAs(JsFunction.class);
        assertThat(new JsIgnoreLiteral().annotationType()).isSameAs(JsIgnore.class);
        assertThat(new JsOverlayLiteral().annotationType()).isSameAs(JsOverlay.class);
        assertThat(new JsOptionalLiteral().annotationType()).isSameAs(JsOptional.class);
    }

    @Test
    void jsIgnoreCanMarkConstructorOverloadsAsJavaOnly() {
        ConstructableToken exportedToken = new ConstructableToken("alpha");
        ConstructableToken javaOnlyToken = new ConstructableToken("beta", "trusted-java");

        assertThat(exportedToken.describe()).isEqualTo("alpha:js-visible");
        assertThat(javaOnlyToken.describe()).isEqualTo("beta:trusted-java");
    }

    private static int applyMapping(int value, ValueMapper mapper) {
        return mapper.map(value);
    }

    @JsType
    private static final class AutoNamedCounter {
        private int value;

        private AutoNamedCounter(int value) {
            this.value = value;
        }

        @JsProperty
        private int currentValue() {
            return value;
        }

        @JsProperty
        private void setCurrentValue(int value) {
            this.value = value;
        }

        @JsMethod
        private int multiply(int factor) {
            return value * factor;
        }

        @JsOverlay
        private String summary() {
            return "value=" + value;
        }
    }

    @JsType(namespace = "test.widgets", name = "Widget")
    private static final class ExportedWidget {
        @JsOverlay
        static final String DEFAULT_TITLE = "untitled";

        @JsProperty(name = "title", namespace = "test.widgets")
        private String title;

        @JsIgnore
        private final String internalId;

        @JsConstructor
        private ExportedWidget(String title, String internalId) {
            this.title = title;
            this.internalId = internalId;
        }

        @JsProperty(name = "title", namespace = "test.widgets")
        private String getTitle() {
            return title;
        }

        @JsMethod(name = "rename", namespace = "test.widgets")
        private String rename(String nextTitle) {
            String previousTitle = title;
            title = nextTitle;
            return previousTitle + "->" + nextTitle;
        }

        @JsOverlay
        private String decoratedTitle() {
            return "Widget[" + title + "]";
        }

        @JsIgnore
        private String internalId() {
            return internalId;
        }
    }

    @JsType(namespace = "test.tokens", name = "Token")
    private static final class ConstructableToken {
        private final String name;
        private final String source;

        @JsConstructor
        private ConstructableToken(String name) {
            this(name, "js-visible");
        }

        @JsIgnore
        private ConstructableToken(String name, String source) {
            this.name = name;
            this.source = source;
        }

        private String describe() {
            return name + ":" + source;
        }
    }

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Counter")
    private interface CounterApi {
        @JsProperty(name = "count")
        int getCount();

        @JsProperty(name = "count")
        void setCount(int count);

        @JsMethod(name = "incrementBy")
        int increment(@JsOptional Integer amount);

        @JsOverlay
        default int incrementOnce() {
            return increment(1);
        }
    }

    private static final class CounterAdapter implements CounterApi {
        private int count;

        private CounterAdapter(int initialCount) {
            this.count = initialCount;
        }

        @Override
        public int getCount() {
            return count;
        }

        @Override
        public void setCount(int count) {
            this.count = count;
        }

        @Override
        public int increment(Integer amount) {
            int effectiveAmount = amount == null ? 1 : amount;
            count += effectiveAmount;
            return count;
        }
    }

    @JsFunction
    private interface ValueMapper {
        int map(int value);
    }

    private abstract static class AnnotationLiteral<T extends Annotation> implements Annotation {
        private final Class<T> annotationType;

        private AnnotationLiteral(Class<T> annotationType) {
            this.annotationType = annotationType;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return annotationType;
        }
    }

    private static final class JsTypeLiteral extends AnnotationLiteral<JsType> implements JsType {
        private final String name;
        private final String namespace;
        private final boolean isNative;

        private JsTypeLiteral(String name, String namespace, boolean isNative) {
            super(JsType.class);
            this.name = name;
            this.namespace = namespace;
            this.isNative = isNative;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String namespace() {
            return namespace;
        }

        @Override
        public boolean isNative() {
            return isNative;
        }
    }

    private static final class JsMethodLiteral extends AnnotationLiteral<JsMethod> implements JsMethod {
        private final String name;
        private final String namespace;

        private JsMethodLiteral(String name, String namespace) {
            super(JsMethod.class);
            this.name = name;
            this.namespace = namespace;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String namespace() {
            return namespace;
        }
    }

    private static final class JsPropertyLiteral extends AnnotationLiteral<JsProperty> implements JsProperty {
        private final String name;
        private final String namespace;

        private JsPropertyLiteral(String name, String namespace) {
            super(JsProperty.class);
            this.name = name;
            this.namespace = namespace;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String namespace() {
            return namespace;
        }
    }

    private static final class JsPackageLiteral extends AnnotationLiteral<JsPackage> implements JsPackage {
        private final String namespace;

        private JsPackageLiteral(String namespace) {
            super(JsPackage.class);
            this.namespace = namespace;
        }

        @Override
        public String namespace() {
            return namespace;
        }
    }

    private static final class JsConstructorLiteral extends AnnotationLiteral<JsConstructor> implements JsConstructor {
        private JsConstructorLiteral() {
            super(JsConstructor.class);
        }
    }

    private static final class JsFunctionLiteral extends AnnotationLiteral<JsFunction> implements JsFunction {
        private JsFunctionLiteral() {
            super(JsFunction.class);
        }
    }

    private static final class JsIgnoreLiteral extends AnnotationLiteral<JsIgnore> implements JsIgnore {
        private JsIgnoreLiteral() {
            super(JsIgnore.class);
        }
    }

    private static final class JsOverlayLiteral extends AnnotationLiteral<JsOverlay> implements JsOverlay {
        private JsOverlayLiteral() {
            super(JsOverlay.class);
        }
    }

    private static final class JsOptionalLiteral extends AnnotationLiteral<JsOptional> implements JsOptional {
        private JsOptionalLiteral() {
            super(JsOptional.class);
        }
    }
}
