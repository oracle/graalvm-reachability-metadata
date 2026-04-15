/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_annotations;

import java.lang.annotation.Annotation;
import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.annotation.ObjectIdGenerator;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Jackson_annotationsTest {
    @Test
    void jsonFormatValueSupportsDefaultStringAndAnnotationInputs() {
        JsonFormat.Value defaultValue = new JsonFormat.Value();

        assertThat(defaultValue.getPattern()).isEmpty();
        assertThat(defaultValue.getShape()).isEqualTo(JsonFormat.Shape.ANY);
        assertThat(defaultValue.getLocale()).isNull();
        assertThat(defaultValue.getTimeZone()).isNull();

        JsonFormat.Value explicitValue = new JsonFormat.Value(
                "yyyy-MM-dd",
                JsonFormat.Shape.STRING,
                "fr",
                "UTC"
        );

        assertThat(explicitValue.getPattern()).isEqualTo("yyyy-MM-dd");
        assertThat(explicitValue.getShape()).isEqualTo(JsonFormat.Shape.STRING);
        assertThat(explicitValue.getLocale()).isEqualTo(Locale.FRENCH);
        assertThat(explicitValue.getTimeZone().getID()).isEqualTo("UTC");

        JsonFormat.Value defaultMarkerValue = new JsonFormat.Value(
                "pattern",
                JsonFormat.Shape.OBJECT,
                JsonFormat.DEFAULT_LOCALE,
                JsonFormat.DEFAULT_TIMEZONE
        );

        assertThat(defaultMarkerValue.getLocale()).isNull();
        assertThat(defaultMarkerValue.getTimeZone()).isNull();

        JsonFormat.Value emptyMarkerValue = new JsonFormat.Value(
                "pattern",
                JsonFormat.Shape.ARRAY,
                "",
                ""
        );

        assertThat(emptyMarkerValue.getLocale()).isNull();
        assertThat(emptyMarkerValue.getTimeZone()).isNull();

        JsonFormat.Value updatedValue = explicitValue
                .withPattern("HH:mm")
                .withShape(JsonFormat.Shape.NUMBER_INT)
                .withLocale(Locale.CANADA_FRENCH)
                .withTimeZone(TimeZone.getTimeZone("GMT+02:00"));

        assertThat(updatedValue.getPattern()).isEqualTo("HH:mm");
        assertThat(updatedValue.getShape()).isEqualTo(JsonFormat.Shape.NUMBER_INT);
        assertThat(updatedValue.getLocale()).isEqualTo(Locale.CANADA_FRENCH);
        assertThat(updatedValue.getTimeZone().getID()).isEqualTo("GMT+02:00");
        assertThat(explicitValue.getPattern()).isEqualTo("yyyy-MM-dd");
        assertThat(explicitValue.getShape()).isEqualTo(JsonFormat.Shape.STRING);
        assertThat(explicitValue.getLocale()).isEqualTo(Locale.FRENCH);
        assertThat(explicitValue.getTimeZone().getID()).isEqualTo("UTC");

        JsonFormat annotation = jsonFormatAnnotation("dd/MM", JsonFormat.Shape.BOOLEAN, "de", "Europe/Berlin");
        JsonFormat.Value annotationValue = new JsonFormat.Value(annotation);

        assertThat(annotationValue.getPattern()).isEqualTo("dd/MM");
        assertThat(annotationValue.getShape()).isEqualTo(JsonFormat.Shape.BOOLEAN);
        assertThat(annotationValue.getLocale()).isEqualTo(Locale.GERMAN);
        assertThat(annotationValue.getTimeZone().getID()).isEqualTo("Europe/Berlin");
    }

    @Test
    void jsonFormatShapeRecognizesNumericAndStructuredKinds() {
        assertThat(JsonFormat.Shape.values()).containsExactly(
                JsonFormat.Shape.ANY,
                JsonFormat.Shape.SCALAR,
                JsonFormat.Shape.ARRAY,
                JsonFormat.Shape.OBJECT,
                JsonFormat.Shape.NUMBER,
                JsonFormat.Shape.NUMBER_FLOAT,
                JsonFormat.Shape.NUMBER_INT,
                JsonFormat.Shape.STRING,
                JsonFormat.Shape.BOOLEAN
        );
        assertThat(JsonFormat.Shape.valueOf("NUMBER_FLOAT")).isEqualTo(JsonFormat.Shape.NUMBER_FLOAT);

        assertThat(JsonFormat.Shape.NUMBER.isNumeric()).isTrue();
        assertThat(JsonFormat.Shape.NUMBER_FLOAT.isNumeric()).isTrue();
        assertThat(JsonFormat.Shape.NUMBER_INT.isNumeric()).isTrue();
        assertThat(JsonFormat.Shape.ANY.isNumeric()).isFalse();
        assertThat(JsonFormat.Shape.SCALAR.isNumeric()).isFalse();
        assertThat(JsonFormat.Shape.ARRAY.isNumeric()).isFalse();
        assertThat(JsonFormat.Shape.OBJECT.isNumeric()).isFalse();
        assertThat(JsonFormat.Shape.STRING.isNumeric()).isFalse();
        assertThat(JsonFormat.Shape.BOOLEAN.isNumeric()).isFalse();

        assertThat(JsonFormat.Shape.ARRAY.isStructured()).isTrue();
        assertThat(JsonFormat.Shape.OBJECT.isStructured()).isTrue();
        assertThat(JsonFormat.Shape.ANY.isStructured()).isFalse();
        assertThat(JsonFormat.Shape.SCALAR.isStructured()).isFalse();
        assertThat(JsonFormat.Shape.NUMBER.isStructured()).isFalse();
        assertThat(JsonFormat.Shape.NUMBER_FLOAT.isStructured()).isFalse();
        assertThat(JsonFormat.Shape.NUMBER_INT.isStructured()).isFalse();
        assertThat(JsonFormat.Shape.STRING.isStructured()).isFalse();
        assertThat(JsonFormat.Shape.BOOLEAN.isStructured()).isFalse();
    }

    @Test
    void propertyAccessorFlagsMatchTheirAccessorKinds() {
        assertThat(PropertyAccessor.values()).containsExactly(
                PropertyAccessor.GETTER,
                PropertyAccessor.SETTER,
                PropertyAccessor.CREATOR,
                PropertyAccessor.FIELD,
                PropertyAccessor.IS_GETTER,
                PropertyAccessor.NONE,
                PropertyAccessor.ALL
        );
        assertThat(PropertyAccessor.valueOf("FIELD")).isEqualTo(PropertyAccessor.FIELD);

        assertThat(PropertyAccessor.GETTER.getterEnabled()).isTrue();
        assertThat(PropertyAccessor.GETTER.setterEnabled()).isFalse();
        assertThat(PropertyAccessor.GETTER.creatorEnabled()).isFalse();
        assertThat(PropertyAccessor.GETTER.fieldEnabled()).isFalse();
        assertThat(PropertyAccessor.GETTER.isGetterEnabled()).isFalse();

        assertThat(PropertyAccessor.SETTER.setterEnabled()).isTrue();
        assertThat(PropertyAccessor.CREATOR.creatorEnabled()).isTrue();
        assertThat(PropertyAccessor.FIELD.fieldEnabled()).isTrue();
        assertThat(PropertyAccessor.IS_GETTER.isGetterEnabled()).isTrue();

        assertThat(PropertyAccessor.NONE.creatorEnabled()).isFalse();
        assertThat(PropertyAccessor.NONE.getterEnabled()).isFalse();
        assertThat(PropertyAccessor.NONE.isGetterEnabled()).isFalse();
        assertThat(PropertyAccessor.NONE.setterEnabled()).isFalse();
        assertThat(PropertyAccessor.NONE.fieldEnabled()).isFalse();

        assertThat(PropertyAccessor.ALL.creatorEnabled()).isTrue();
        assertThat(PropertyAccessor.ALL.getterEnabled()).isTrue();
        assertThat(PropertyAccessor.ALL.isGetterEnabled()).isTrue();
        assertThat(PropertyAccessor.ALL.setterEnabled()).isTrue();
        assertThat(PropertyAccessor.ALL.fieldEnabled()).isTrue();
    }

    @Test
    void jsonTypeInfoAndJsonIncludeEnumsExposeStableDefaults() {
        assertThat(JsonTypeInfo.Id.values()).containsExactly(
                JsonTypeInfo.Id.NONE,
                JsonTypeInfo.Id.CLASS,
                JsonTypeInfo.Id.MINIMAL_CLASS,
                JsonTypeInfo.Id.NAME,
                JsonTypeInfo.Id.CUSTOM
        );
        assertThat(JsonTypeInfo.Id.valueOf("CLASS")).isEqualTo(JsonTypeInfo.Id.CLASS);
        assertThat(JsonTypeInfo.Id.NONE.getDefaultPropertyName()).isNull();
        assertThat(JsonTypeInfo.Id.CLASS.getDefaultPropertyName()).isEqualTo("@class");
        assertThat(JsonTypeInfo.Id.MINIMAL_CLASS.getDefaultPropertyName()).isEqualTo("@c");
        assertThat(JsonTypeInfo.Id.NAME.getDefaultPropertyName()).isEqualTo("@type");
        assertThat(JsonTypeInfo.Id.CUSTOM.getDefaultPropertyName()).isNull();

        assertThat(JsonTypeInfo.As.values()).containsExactly(
                JsonTypeInfo.As.PROPERTY,
                JsonTypeInfo.As.WRAPPER_OBJECT,
                JsonTypeInfo.As.WRAPPER_ARRAY,
                JsonTypeInfo.As.EXTERNAL_PROPERTY
        );
        assertThat(JsonTypeInfo.As.valueOf("WRAPPER_ARRAY")).isEqualTo(JsonTypeInfo.As.WRAPPER_ARRAY);

        assertThat(JsonInclude.Include.values()).containsExactly(
                JsonInclude.Include.ALWAYS,
                JsonInclude.Include.NON_NULL,
                JsonInclude.Include.NON_DEFAULT,
                JsonInclude.Include.NON_EMPTY
        );
        assertThat(JsonInclude.Include.valueOf("NON_EMPTY")).isEqualTo(JsonInclude.Include.NON_EMPTY);
    }

    @Test
    void jsonSubTypesRetainsNamedSubtypeDefinitions() {
        JsonSubTypes.Type firstSubtype = jsonSubTypeAnnotation(FirstSubtype.class, "first");
        JsonSubTypes.Type secondSubtype = jsonSubTypeAnnotation(SecondSubtype.class, "second");
        JsonSubTypes jsonSubTypes = jsonSubTypesAnnotation(firstSubtype, secondSubtype);

        assertThat(jsonSubTypes.value()).containsExactly(firstSubtype, secondSubtype);
        assertThat(jsonSubTypes.value())
                .extracting(JsonSubTypes.Type::value)
                .containsExactly(FirstSubtype.class, SecondSubtype.class);
        assertThat(jsonSubTypes.value())
                .extracting(JsonSubTypes.Type::name)
                .containsExactly("first", "second");
    }

    @Test
    void propertyAnnotationsExposeIgnoreOrderAndUnwrapConfiguration() {
        JsonIgnoreProperties ignoreProperties = jsonIgnorePropertiesAnnotation(true, "internalId", "debugOnly");
        JsonPropertyOrder propertyOrder = jsonPropertyOrderAnnotation(true, "id", "name", "createdAt");
        JsonUnwrapped unwrapped = jsonUnwrappedAnnotation(true, "address.", ".value");

        assertThat(ignoreProperties.value()).containsExactly("internalId", "debugOnly");
        assertThat(ignoreProperties.ignoreUnknown()).isTrue();

        assertThat(propertyOrder.value()).containsExactly("id", "name", "createdAt");
        assertThat(propertyOrder.alphabetic()).isTrue();

        assertThat(unwrapped.enabled()).isTrue();
        assertThat(unwrapped.prefix()).isEqualTo("address.");
        assertThat(unwrapped.suffix()).isEqualTo(".value");
    }

    @Test
    void visibilityRulesDependOnMemberModifiers() {
        Member publicMember = memberWithModifiers(Modifier.PUBLIC);
        Member protectedMember = memberWithModifiers(Modifier.PROTECTED);
        Member packagePrivateMember = memberWithModifiers(0);
        Member privateMember = memberWithModifiers(Modifier.PRIVATE);

        assertThat(JsonAutoDetect.Visibility.values()).containsExactly(
                JsonAutoDetect.Visibility.ANY,
                JsonAutoDetect.Visibility.NON_PRIVATE,
                JsonAutoDetect.Visibility.PROTECTED_AND_PUBLIC,
                JsonAutoDetect.Visibility.PUBLIC_ONLY,
                JsonAutoDetect.Visibility.NONE,
                JsonAutoDetect.Visibility.DEFAULT
        );
        assertThat(JsonAutoDetect.Visibility.valueOf("PUBLIC_ONLY")).isEqualTo(JsonAutoDetect.Visibility.PUBLIC_ONLY);

        assertThat(JsonAutoDetect.Visibility.ANY.isVisible(privateMember)).isTrue();
        assertThat(JsonAutoDetect.Visibility.NONE.isVisible(publicMember)).isFalse();
        assertThat(JsonAutoDetect.Visibility.DEFAULT.isVisible(publicMember)).isFalse();

        assertThat(JsonAutoDetect.Visibility.NON_PRIVATE.isVisible(publicMember)).isTrue();
        assertThat(JsonAutoDetect.Visibility.NON_PRIVATE.isVisible(protectedMember)).isTrue();
        assertThat(JsonAutoDetect.Visibility.NON_PRIVATE.isVisible(packagePrivateMember)).isTrue();
        assertThat(JsonAutoDetect.Visibility.NON_PRIVATE.isVisible(privateMember)).isFalse();

        assertThat(JsonAutoDetect.Visibility.PROTECTED_AND_PUBLIC.isVisible(publicMember)).isTrue();
        assertThat(JsonAutoDetect.Visibility.PROTECTED_AND_PUBLIC.isVisible(protectedMember)).isTrue();
        assertThat(JsonAutoDetect.Visibility.PROTECTED_AND_PUBLIC.isVisible(packagePrivateMember)).isFalse();
        assertThat(JsonAutoDetect.Visibility.PROTECTED_AND_PUBLIC.isVisible(privateMember)).isFalse();

        assertThat(JsonAutoDetect.Visibility.PUBLIC_ONLY.isVisible(publicMember)).isTrue();
        assertThat(JsonAutoDetect.Visibility.PUBLIC_ONLY.isVisible(protectedMember)).isFalse();
        assertThat(JsonAutoDetect.Visibility.PUBLIC_ONLY.isVisible(packagePrivateMember)).isFalse();
        assertThat(JsonAutoDetect.Visibility.PUBLIC_ONLY.isVisible(privateMember)).isFalse();
    }

    @Test
    void objectIdKeyEqualityDependsOnTypeScopeAndKey() {
        ObjectIdGenerator.IdKey baseKey = new ObjectIdGenerator.IdKey(String.class, Integer.class, "alpha");
        ObjectIdGenerator.IdKey equalKey = new ObjectIdGenerator.IdKey(String.class, Integer.class, "alpha");
        ObjectIdGenerator.IdKey differentType = new ObjectIdGenerator.IdKey(UUID.class, Integer.class, "alpha");
        ObjectIdGenerator.IdKey differentScope = new ObjectIdGenerator.IdKey(String.class, Long.class, "alpha");
        ObjectIdGenerator.IdKey differentKey = new ObjectIdGenerator.IdKey(String.class, Integer.class, "beta");
        ObjectIdGenerator.IdKey nullScope = new ObjectIdGenerator.IdKey(String.class, null, "alpha");

        assertThat(baseKey).isEqualTo(baseKey);
        assertThat(baseKey).isEqualTo(equalKey);
        assertThat(baseKey.hashCode()).isEqualTo(equalKey.hashCode());
        assertThat(baseKey).isNotEqualTo(differentType);
        assertThat(baseKey).isNotEqualTo(differentScope);
        assertThat(baseKey).isNotEqualTo(differentKey);
        assertThat(baseKey).isNotEqualTo(nullScope);
        assertThat(baseKey).isNotEqualTo(null);
        assertThat(baseKey).isNotEqualTo("alpha");
    }

    @Test
    void intSequenceGeneratorTracksScopeKeysAndSerializationState() {
        ObjectIdGenerators.IntSequenceGenerator generator = new ObjectIdGenerators.IntSequenceGenerator();

        assertThat(generator.getScope()).isEqualTo(Object.class);
        assertThat(generator.generateId(new Object())).isEqualTo(-1);
        assertThat(generator.generateId(new Object())).isEqualTo(0);

        assertThat(generator.forScope(Object.class)).isSameAs(generator);

        ObjectIdGenerator<Integer> scopedGenerator = generator.forScope(String.class);
        assertThat(scopedGenerator).isNotSameAs(generator);
        assertThat(scopedGenerator.getScope()).isEqualTo(String.class);
        assertThat(scopedGenerator.generateId(new Object())).isEqualTo(1);

        ObjectIdGenerator<Integer> serializationGenerator = generator.newForSerialization(new Object());
        assertThat(serializationGenerator).isNotSameAs(generator);
        assertThat(serializationGenerator.getScope()).isEqualTo(Object.class);
        assertThat(serializationGenerator.generateId(new Object())).isEqualTo(1);
        assertThat(serializationGenerator.generateId(new Object())).isEqualTo(2);

        assertThat(generator.key("user-1")).isEqualTo(
                new ObjectIdGenerator.IdKey(ObjectIdGenerators.IntSequenceGenerator.class, Object.class, "user-1")
        );

        assertThat(generator.canUseFor(new ObjectIdGenerators.IntSequenceGenerator(Object.class, 25))).isTrue();
        assertThat(generator.canUseFor(new ObjectIdGenerators.IntSequenceGenerator(String.class, 25))).isFalse();
        assertThat(generator.canUseFor(new ObjectIdGenerators.UUIDGenerator())).isFalse();
    }

    @Test
    void uuidGeneratorIsReusableAcrossScopesAndProducesUniqueIds() {
        ObjectIdGenerators.UUIDGenerator generator = new ObjectIdGenerators.UUIDGenerator();

        assertThat(generator.getScope()).isEqualTo(Object.class);
        assertThat(generator.forScope(String.class)).isSameAs(generator);
        assertThat(generator.newForSerialization(new Object())).isSameAs(generator);

        UUID firstId = generator.generateId(new Object());
        UUID secondId = generator.generateId(new Object());

        assertThat(firstId).isNotNull();
        assertThat(secondId).isNotNull();
        assertThat(secondId).isNotEqualTo(firstId);
        assertThat(generator.key("user-2")).isEqualTo(
                new ObjectIdGenerator.IdKey(ObjectIdGenerators.UUIDGenerator.class, null, "user-2")
        );
        assertThat(generator.canUseFor(new ObjectIdGenerators.UUIDGenerator())).isTrue();
        assertThat(generator.canUseFor(new ObjectIdGenerators.IntSequenceGenerator())).isFalse();
    }

    private static JsonFormat jsonFormatAnnotation(
            final String pattern,
            final JsonFormat.Shape shape,
            final String locale,
            final String timezone
    ) {
        return new JsonFormat() {
            @Override
            public String pattern() {
                return pattern;
            }

            @Override
            public JsonFormat.Shape shape() {
                return shape;
            }

            @Override
            public String locale() {
                return locale;
            }

            @Override
            public String timezone() {
                return timezone;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return JsonFormat.class;
            }
        };
    }

    private static JsonSubTypes jsonSubTypesAnnotation(final JsonSubTypes.Type... value) {
        return new JsonSubTypes() {
            @Override
            public JsonSubTypes.Type[] value() {
                return value;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return JsonSubTypes.class;
            }
        };
    }

    private static JsonSubTypes.Type jsonSubTypeAnnotation(final Class<?> value, final String name) {
        return new JsonSubTypes.Type() {
            @Override
            public Class<?> value() {
                return value;
            }

            @Override
            public String name() {
                return name;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return JsonSubTypes.Type.class;
            }
        };
    }

    private static JsonIgnoreProperties jsonIgnorePropertiesAnnotation(
            final boolean ignoreUnknown,
            final String... value
    ) {
        return new JsonIgnoreProperties() {
            @Override
            public String[] value() {
                return value;
            }

            @Override
            public boolean ignoreUnknown() {
                return ignoreUnknown;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return JsonIgnoreProperties.class;
            }
        };
    }

    private static JsonPropertyOrder jsonPropertyOrderAnnotation(
            final boolean alphabetic,
            final String... value
    ) {
        return new JsonPropertyOrder() {
            @Override
            public String[] value() {
                return value;
            }

            @Override
            public boolean alphabetic() {
                return alphabetic;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return JsonPropertyOrder.class;
            }
        };
    }

    private static JsonUnwrapped jsonUnwrappedAnnotation(
            final boolean enabled,
            final String prefix,
            final String suffix
    ) {
        return new JsonUnwrapped() {
            @Override
            public boolean enabled() {
                return enabled;
            }

            @Override
            public String prefix() {
                return prefix;
            }

            @Override
            public String suffix() {
                return suffix;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return JsonUnwrapped.class;
            }
        };
    }

    private static Member memberWithModifiers(final int modifiers) {
        return new Member() {
            @Override
            public Class<?> getDeclaringClass() {
                return Jackson_annotationsTest.class;
            }

            @Override
            public String getName() {
                return "member";
            }

            @Override
            public int getModifiers() {
                return modifiers;
            }

            @Override
            public boolean isSynthetic() {
                return false;
            }
        };
    }

    private static final class FirstSubtype {
    }

    private static final class SecondSubtype {
    }
}
