/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.fastjson;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;
import com.alibaba.fastjson.parser.ParserConfig;
import com.alibaba.fastjson.util.FieldInfo;
import com.alibaba.fastjson.util.TypeUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.Year;
import java.time.YearMonth;
import java.util.AbstractCollection;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import kotlin.Metadata;
import org.junit.jupiter.api.Test;

public class TypeUtilsTest {
    @Test
    void loadClassResolvesArraysAndFallbackClassLoaders() {
        Class<?> classLoaderResult = TypeUtils.loadClass("example.Year", new ResolvingClassLoader("example.Year", Year.class));
        assertThat(classLoaderResult).isEqualTo(Year.class);

        Class<?> contextLoaderResult;
        Class<?> classForNameResult;
        ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(new ResolvingClassLoader("java.time.MonthDay", MonthDay.class));
            contextLoaderResult = TypeUtils.loadClass("java.time.MonthDay", null);

            Thread.currentThread().setContextClassLoader(null);
            classForNameResult = TypeUtils.loadClass("java.time.YearMonth", null);
        } finally {
            Thread.currentThread().setContextClassLoader(previousClassLoader);
        }
        assertThat(contextLoaderResult).isEqualTo(MonthDay.class);
        assertThat(classForNameResult).isEqualTo(YearMonth.class);
        assertThat(TypeUtils.loadClass("[java.time.LocalTime", null)).isEqualTo(LocalTime[].class);
    }

    @Test
    void castAndFactoryMethodsCreateArraysCalendarsAndCustomCollections() {
        int[] numbers = TypeUtils.cast(Arrays.asList("1", "2", "3"), int[].class, ParserConfig.getGlobalInstance());
        assertThat(numbers).containsExactly(1, 2, 3);

        CustomCalendar calendar = TypeUtils.cast(new Date(0L), CustomCalendar.class, ParserConfig.getGlobalInstance());
        assertThat(calendar.getTime()).isEqualTo(new Date(0L));

        Set customSet = TypeUtils.createSet(CustomSet.class);
        Collection customCollection = TypeUtils.createCollection(CustomCollection.class);
        assertThat(customSet).isInstanceOf(CustomSet.class);
        assertThat(customCollection).isInstanceOf(CustomCollection.class);
    }

    @Test
    void castToJavaBeanCreatesProxyForInterfaces() {
        Map<String, Object> values = Collections.singletonMap("name", "fastjson");
        NamedView proxy = TypeUtils.castToJavaBean(values, NamedView.class, ParserConfig.getGlobalInstance());
        assertThat(proxy.getName()).isEqualTo("fastjson");
    }

    @Test
    void optionalEmptyUsesOptionalFactoryMethod() {
        Object optional = TypeUtils.optionalEmpty(Optional.class);
        assertThat(optional).isEqualTo(Optional.empty());
    }

    @Test
    void checkPrimitiveArrayResolvesPrimitiveGenericArrays() {
        Type resolvedType = TypeUtils.checkPrimitiveArray(new PrimitiveGenericArrayType(int.class));
        assertThat(resolvedType).isEqualTo(int[].class);
    }

    @Test
    void xmlAccessorTypeAnnotationsAreReadThroughPublicUtility() {
        assertThat(TypeUtils.isXmlField(XmlFieldBean.class)).isTrue();
    }

    @Test
    void computeGettersCoversMethodsFieldsSuperTypesAndKotlinConstructorDiscovery() {
        List<FieldInfo> fieldBased = TypeUtils.computeGettersWithFieldBase(FieldBasedBean.class, null, true, null);
        assertThat(fieldBased).extracting(fieldInfo -> fieldInfo.name).contains("publicValue", "privateValue");

        List<FieldInfo> getters = TypeUtils.computeGetters(ConcreteAnnotatedBean.class, null, true);
        assertThat(getters).extracting(fieldInfo -> fieldInfo.name).contains("ifaceName", "abstractName", "publicValue");

        List<FieldInfo> kotlinLikeGetters = TypeUtils.computeGetters(KotlinLikeBean.class, null, true);
        assertThat(kotlinLikeGetters).extracting(fieldInfo -> fieldInfo.name).contains("value");
    }

    @Test
    void getFieldSearchesDeclaredFieldsOnSuperclasses() {
        Field field = TypeUtils.getField(ChildFieldBean.class, "baseValue", ChildFieldBean.class.getDeclaredFields());
        assertThat(field).isNotNull();
        assertThat(field.getDeclaringClass()).isEqualTo(BaseFieldBean.class);
    }

    @Test
    void mixInAnnotationsAreReadFromFieldsMethodsAndParameters() throws NoSuchMethodException, NoSuchFieldException {
        JSON.addMixInAnnotations(FieldAnnotationTarget.class, FieldAnnotationMixin.class);
        JSON.addMixInAnnotations(MethodAnnotationTarget.class, MethodAnnotationMixin.class);
        JSON.addMixInAnnotations(MethodParameterTarget.class, MethodParameterMixin.class);
        try {
            Field field = FieldAnnotationTarget.class.getField("name");
            JSONField fieldAnnotation = TypeUtils.getAnnotation(field, JSONField.class);
            assertThat(fieldAnnotation.name()).isEqualTo("mixedField");

            Method method = MethodAnnotationTarget.class.getMethod("getName");
            JSONField methodAnnotation = TypeUtils.getAnnotation(method, JSONField.class);
            assertThat(methodAnnotation.name()).isEqualTo("mixedMethod");

            Method parameterMethod = MethodParameterTarget.class.getMethod("setName", String.class);
            Annotation[][] parameterAnnotations = TypeUtils.getParameterAnnotations(parameterMethod);
            assertThat(Arrays.stream(parameterAnnotations[0]).map(Annotation::annotationType)).contains(JSONField.class);
        } finally {
            JSON.removeMixInAnnotations(FieldAnnotationTarget.class);
            JSON.removeMixInAnnotations(MethodAnnotationTarget.class);
            JSON.removeMixInAnnotations(MethodParameterTarget.class);
        }
    }

    @Test
    void mixInConstructorParameterAnnotationsAreReadForTopLevelAndInnerMixIns() throws NoSuchMethodException {
        JSON.addMixInAnnotations(ConstructorAnnotationTarget.class, TopLevelConstructorAnnotationMixin.class);
        JSON.addMixInAnnotations(InnerConstructorAnnotationTarget.class, InnerConstructorAnnotationMixin.class);
        try {
            Constructor<ConstructorAnnotationTarget> constructor = ConstructorAnnotationTarget.class.getConstructor(String.class);
            Annotation[][] parameterAnnotations = TypeUtils.getParameterAnnotations(constructor);
            assertThat(Arrays.stream(parameterAnnotations[0]).map(Annotation::annotationType)).contains(JSONField.class);

            Constructor<InnerConstructorAnnotationTarget> innerConstructor = InnerConstructorAnnotationTarget.class.getConstructor(String.class);
            Annotation[][] innerParameterAnnotations = TypeUtils.getParameterAnnotations(innerConstructor);
            assertThat(Arrays.stream(innerParameterAnnotations).flatMap(Arrays::stream).map(Annotation::annotationType))
                    .contains(JSONField.class);
        } finally {
            JSON.removeMixInAnnotations(ConstructorAnnotationTarget.class);
            JSON.removeMixInAnnotations(InnerConstructorAnnotationTarget.class);
        }
    }

    public interface NamedView {
        String getName();
    }

    public static class CustomCalendar extends GregorianCalendar {
    }

    public static class CustomSet extends HashSet<String> {
    }

    public static class CustomCollection extends AbstractCollection<String> {
        @Override
        public Iterator<String> iterator() {
            return Collections.emptyIterator();
        }

        @Override
        public int size() {
            return 0;
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class XmlFieldBean {
        public String value;
    }

    public static class FieldBasedBean {
        public String publicValue;
        private String privateValue;
    }

    public interface AnnotatedPropertyInterface {
        @JSONField(name = "ifaceName")
        String getInterfaceName();
    }

    public abstract static class AbstractAnnotatedProperty {
        @JSONField(name = "abstractName")
        public abstract String getAbstractName();
    }

    public static class ConcreteAnnotatedBean extends AbstractAnnotatedProperty implements AnnotatedPropertyInterface {
        public String publicValue;

        @Override
        public String getInterfaceName() {
            return "iface";
        }

        @Override
        public String getAbstractName() {
            return "abstract";
        }
    }

    @Metadata(k = 1, mv = {1, 9, 0}, d1 = {}, d2 = {})
    public static class KotlinLikeBean {
        private final String value;

        public KotlinLikeBean(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public static class BaseFieldBean {
        public String baseValue;
    }

    public static class ChildFieldBean extends BaseFieldBean {
        public String childValue;
    }

    public static class FieldAnnotationTarget {
        public String name;
    }

    public static class FieldAnnotationMixin {
        @JSONField(name = "mixedField")
        public String name;
    }

    public static class MethodAnnotationTarget {
        public String getName() {
            return "target";
        }
    }

    public static class MethodAnnotationMixin {
        @JSONField(name = "mixedMethod")
        public String getName() {
            return "mixin";
        }
    }

    public static class MethodParameterTarget {
        public void setName(String name) {
        }
    }

    public static class MethodParameterMixin {
        public void setName(@JSONField(name = "mixedParameter") String name) {
        }
    }

    public static class ConstructorAnnotationTarget {
        public ConstructorAnnotationTarget(String name) {
        }
    }

    public static class InnerConstructorAnnotationTarget {
        public InnerConstructorAnnotationTarget(String name) {
        }
    }

    public class InnerConstructorAnnotationMixin {
        public InnerConstructorAnnotationMixin(@JSONField(name = "mixedInnerConstructorParameter") String name) {
        }
    }

    private static class PrimitiveGenericArrayType implements GenericArrayType {
        private final Type componentType;

        PrimitiveGenericArrayType(Type componentType) {
            this.componentType = componentType;
        }

        @Override
        public Type getGenericComponentType() {
            return componentType;
        }
    }

    private static class ResolvingClassLoader extends ClassLoader {
        private final String supportedName;
        private final Class<?> resolvedClass;

        ResolvingClassLoader(String supportedName, Class<?> resolvedClass) {
            super(null);
            this.supportedName = supportedName;
            this.resolvedClass = resolvedClass;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (supportedName.equals(name)) {
                return resolvedClass;
            }
            throw new ClassNotFoundException(name);
        }
    }

}

class TopLevelConstructorAnnotationMixin {
    TopLevelConstructorAnnotationMixin(@JSONField(name = "mixedConstructorParameter") String name) {
    }
}
