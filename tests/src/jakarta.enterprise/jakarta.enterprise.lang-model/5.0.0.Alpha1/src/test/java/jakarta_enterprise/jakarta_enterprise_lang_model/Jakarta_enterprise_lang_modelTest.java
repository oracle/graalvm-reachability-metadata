/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_enterprise.jakarta_enterprise_lang_model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.enterprise.lang.model.AnnotationInfo;
import jakarta.enterprise.lang.model.AnnotationMember;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.enterprise.lang.model.declarations.DeclarationInfo;
import jakarta.enterprise.lang.model.declarations.FieldInfo;
import jakarta.enterprise.lang.model.declarations.MethodInfo;
import jakarta.enterprise.lang.model.declarations.PackageInfo;
import jakarta.enterprise.lang.model.declarations.ParameterInfo;
import jakarta.enterprise.lang.model.declarations.RecordComponentInfo;
import jakarta.enterprise.lang.model.types.ArrayType;
import jakarta.enterprise.lang.model.types.ClassType;
import jakarta.enterprise.lang.model.types.ParameterizedType;
import jakarta.enterprise.lang.model.types.PrimitiveType;
import jakarta.enterprise.lang.model.types.Type;
import jakarta.enterprise.lang.model.types.TypeVariable;
import jakarta.enterprise.lang.model.types.VoidType;
import jakarta.enterprise.lang.model.types.WildcardType;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

public class Jakarta_enterprise_lang_modelTest {
    private static final int PUBLIC_MODIFIER = 0x0001;
    private static final int FINAL_MODIFIER = 0x0010;

    @Test
    void typeDefaultMethodsClassifyEveryKindAndRejectInvalidCasts() {
        for (Type.Kind kind : Type.Kind.values()) {
            Type type = new GenericType(kind);

            assertThat(type.isType()).isTrue();
            assertThat(type.isDeclaration()).isFalse();
            assertThat(type.asType()).isSameAs(type);
            assertThat(type.isVoid()).isEqualTo(kind == Type.Kind.VOID);
            assertThat(type.isPrimitive()).isEqualTo(kind == Type.Kind.PRIMITIVE);
            assertThat(type.isClass()).isEqualTo(kind == Type.Kind.CLASS);
            assertThat(type.isArray()).isEqualTo(kind == Type.Kind.ARRAY);
            assertThat(type.isParameterizedType()).isEqualTo(kind == Type.Kind.PARAMETERIZED_TYPE);
            assertThat(type.isTypeVariable()).isEqualTo(kind == Type.Kind.TYPE_VARIABLE);
            assertThat(type.isWildcardType()).isEqualTo(kind == Type.Kind.WILDCARD_TYPE);
        }

        Type type = new GenericType(Type.Kind.CLASS);
        assertThatThrownBy(type::asDeclaration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Not a declaration");
        assertThatThrownBy(type::asVoid).isInstanceOf(IllegalStateException.class).hasMessage("Not a void");
        assertThatThrownBy(type::asPrimitive).isInstanceOf(IllegalStateException.class).hasMessage("Not a primitive");
        assertThatThrownBy(type::asClass).isInstanceOf(IllegalStateException.class).hasMessage("Not a class");
        assertThatThrownBy(type::asArray).isInstanceOf(IllegalStateException.class).hasMessage("Not an array");
        assertThatThrownBy(type::asParameterizedType)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Not a parameterized type");
        assertThatThrownBy(type::asTypeVariable)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Not a type variable");
        assertThatThrownBy(type::asWildcardType)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Not a wildcard type");
    }

    @Test
    void specializedTypeInterfacesExposeKindSpecificAccessors() {
        FakeVoidType voidType = new FakeVoidType("void");
        FakePrimitiveType intType = new FakePrimitiveType("int", PrimitiveType.PrimitiveKind.INT);
        FakeClassInfo stringDeclaration = new FakeClassInfo("java.lang.String", "String");
        FakeClassType stringType = new FakeClassType(stringDeclaration);
        FakeArrayType arrayType = new FakeArrayType(stringType);
        FakeClassInfo listDeclaration = new FakeClassInfo("java.util.List", "List");
        FakeParameterizedType listOfString = new FakeParameterizedType(
                new FakeClassType(listDeclaration), List.of(stringType));
        FakeTypeVariable variable = new FakeTypeVariable("T", List.of(stringType));
        FakeWildcardType wildcard = new FakeWildcardType(stringType, intType);

        assertThat(voidType.kind()).isEqualTo(Type.Kind.VOID);
        assertThat(voidType.asVoid()).isSameAs(voidType);
        assertThat(voidType.name()).isEqualTo("void");

        assertThat(intType.kind()).isEqualTo(Type.Kind.PRIMITIVE);
        assertThat(intType.asPrimitive()).isSameAs(intType);
        assertThat(intType.name()).isEqualTo("int");

        assertThat(stringType.kind()).isEqualTo(Type.Kind.CLASS);
        assertThat(stringType.asClass()).isSameAs(stringType);
        assertThat(stringType.declaration()).isSameAs(stringDeclaration);

        assertThat(arrayType.kind()).isEqualTo(Type.Kind.ARRAY);
        assertThat(arrayType.asArray()).isSameAs(arrayType);
        assertThat(arrayType.componentType()).isSameAs(stringType);

        assertThat(listOfString.kind()).isEqualTo(Type.Kind.PARAMETERIZED_TYPE);
        assertThat(listOfString.asParameterizedType()).isSameAs(listOfString);
        assertThat(listOfString.genericClass().declaration()).isSameAs(listDeclaration);
        assertThat(listOfString.declaration()).isSameAs(listDeclaration);
        assertThat(listOfString.typeArguments()).containsExactly(stringType);

        assertThat(variable.kind()).isEqualTo(Type.Kind.TYPE_VARIABLE);
        assertThat(variable.asTypeVariable()).isSameAs(variable);
        assertThat(variable.name()).isEqualTo("T");
        assertThat(variable.bounds()).containsExactly(stringType);

        assertThat(wildcard.kind()).isEqualTo(Type.Kind.WILDCARD_TYPE);
        assertThat(wildcard.asWildcardType()).isSameAs(wildcard);
        assertThat(wildcard.upperBound()).isSameAs(stringType);
        assertThat(wildcard.lowerBound()).isSameAs(intType);
    }

    @Test
    void primitiveTypeDefaultMethodsClassifyEveryPrimitiveKind() {
        for (PrimitiveType.PrimitiveKind primitiveKind : PrimitiveType.PrimitiveKind.values()) {
            PrimitiveType primitiveType = new FakePrimitiveType(primitiveKind.name().toLowerCase(), primitiveKind);

            assertThat(primitiveType.kind()).isEqualTo(Type.Kind.PRIMITIVE);
            assertThat(primitiveType.isBoolean()).isEqualTo(primitiveKind == PrimitiveType.PrimitiveKind.BOOLEAN);
            assertThat(primitiveType.isByte()).isEqualTo(primitiveKind == PrimitiveType.PrimitiveKind.BYTE);
            assertThat(primitiveType.isShort()).isEqualTo(primitiveKind == PrimitiveType.PrimitiveKind.SHORT);
            assertThat(primitiveType.isInt()).isEqualTo(primitiveKind == PrimitiveType.PrimitiveKind.INT);
            assertThat(primitiveType.isLong()).isEqualTo(primitiveKind == PrimitiveType.PrimitiveKind.LONG);
            assertThat(primitiveType.isFloat()).isEqualTo(primitiveKind == PrimitiveType.PrimitiveKind.FLOAT);
            assertThat(primitiveType.isDouble()).isEqualTo(primitiveKind == PrimitiveType.PrimitiveKind.DOUBLE);
            assertThat(primitiveType.isChar()).isEqualTo(primitiveKind == PrimitiveType.PrimitiveKind.CHAR);
        }
    }

    @Test
    void declarationDefaultMethodsClassifyEveryKindAndRejectInvalidCasts() {
        for (DeclarationInfo.Kind kind : DeclarationInfo.Kind.values()) {
            DeclarationInfo declaration = new GenericDeclaration(kind);

            assertThat(declaration.isDeclaration()).isTrue();
            assertThat(declaration.isType()).isFalse();
            assertThat(declaration.asDeclaration()).isSameAs(declaration);
            assertThat(declaration.isPackage()).isEqualTo(kind == DeclarationInfo.Kind.PACKAGE);
            assertThat(declaration.isClass()).isEqualTo(kind == DeclarationInfo.Kind.CLASS);
            assertThat(declaration.isMethod()).isEqualTo(kind == DeclarationInfo.Kind.METHOD);
            assertThat(declaration.isParameter()).isEqualTo(kind == DeclarationInfo.Kind.PARAMETER);
            assertThat(declaration.isField()).isEqualTo(kind == DeclarationInfo.Kind.FIELD);
            assertThat(declaration.isRecordComponent()).isEqualTo(kind == DeclarationInfo.Kind.RECORD_COMPONENT);
        }

        DeclarationInfo declaration = new GenericDeclaration(DeclarationInfo.Kind.CLASS);
        assertThatThrownBy(declaration::asType).isInstanceOf(IllegalStateException.class).hasMessage("Not a type");
        assertThatThrownBy(declaration::asPackage)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Not a package");
        assertThatThrownBy(declaration::asClass).isInstanceOf(IllegalStateException.class).hasMessage("Not a class");
        assertThatThrownBy(declaration::asMethod).isInstanceOf(IllegalStateException.class).hasMessage("Not a method");
        assertThatThrownBy(declaration::asParameter)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Not a parameter");
        assertThatThrownBy(declaration::asField).isInstanceOf(IllegalStateException.class).hasMessage("Not a field");
        assertThatThrownBy(declaration::asRecordComponent)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Not a record component");
    }

    @Test
    void specializedDeclarationInterfacesExposeKindSpecificAccessors() {
        FakePackageInfo packageInfo = new FakePackageInfo("example.model");
        FakeClassInfo owner = new FakeClassInfo("example.model.Owner", "Owner");
        owner.packageInfo = packageInfo;
        owner.modifiers = PUBLIC_MODIFIER;
        owner.plainClass = true;
        owner.finalClass = true;
        FakePrimitiveType intType = new FakePrimitiveType("int", PrimitiveType.PrimitiveKind.INT);
        FakeFieldInfo field = new FakeFieldInfo("count", intType, owner, false, true, FINAL_MODIFIER);
        FakeMethodInfo method = new FakeMethodInfo("count", intType, owner, false, false, false, true, PUBLIC_MODIFIER);
        FakeParameterInfo parameter = new FakeParameterInfo("fallback", intType, method);
        FakeRecordComponentInfo component = new FakeRecordComponentInfo("count", intType, field, method, owner);
        owner.fields = List.of(field);
        owner.methods = List.of(method);
        owner.constructors = List.of(
                new FakeMethodInfo("<init>", new FakeVoidType("void"), owner, true, false, false, false, 0));
        owner.recordComponents = List.of(component);
        method.parameters = List.of(parameter);
        method.throwsTypes = List.of(new FakeClassType(new FakeClassInfo("java.io.IOException", "IOException")));
        method.typeParameters = List.of(new FakeTypeVariable("N", List.of(intType)));

        assertThat(packageInfo.kind()).isEqualTo(DeclarationInfo.Kind.PACKAGE);
        assertThat(packageInfo.asPackage()).isSameAs(packageInfo);
        assertThat(packageInfo.name()).isEqualTo("example.model");

        assertThat(owner.kind()).isEqualTo(DeclarationInfo.Kind.CLASS);
        assertThat(owner.asClass()).isSameAs(owner);
        assertThat(owner.name()).isEqualTo("example.model.Owner");
        assertThat(owner.simpleName()).isEqualTo("Owner");
        assertThat(owner.packageInfo()).isSameAs(packageInfo);
        assertThat(owner.isPlainClass()).isTrue();
        assertThat(owner.isInterface()).isFalse();
        assertThat(owner.isEnum()).isFalse();
        assertThat(owner.isAnnotation()).isFalse();
        assertThat(owner.isRecord()).isFalse();
        assertThat(owner.isAbstract()).isFalse();
        assertThat(owner.isFinal()).isTrue();
        assertThat(owner.modifiers()).isEqualTo(PUBLIC_MODIFIER);
        assertThat(owner.fields()).containsExactly(field);
        assertThat(owner.methods()).containsExactly(method);
        assertThat(owner.constructors()).hasSize(1);
        assertThat(owner.recordComponents()).containsExactly(component);

        assertThat(field.kind()).isEqualTo(DeclarationInfo.Kind.FIELD);
        assertThat(field.asField()).isSameAs(field);
        assertThat(field.name()).isEqualTo("count");
        assertThat(field.type()).isSameAs(intType);
        assertThat(field.declaringClass()).isSameAs(owner);
        assertThat(field.isStatic()).isFalse();
        assertThat(field.isFinal()).isTrue();
        assertThat(field.modifiers()).isEqualTo(FINAL_MODIFIER);

        assertThat(method.kind()).isEqualTo(DeclarationInfo.Kind.METHOD);
        assertThat(method.asMethod()).isSameAs(method);
        assertThat(method.name()).isEqualTo("count");
        assertThat(method.returnType()).isSameAs(intType);
        assertThat(method.receiverType()).isNull();
        assertThat(method.declaringClass()).isSameAs(owner);
        assertThat(method.parameters()).containsExactly(parameter);
        assertThat(method.throwsTypes()).hasSize(1);
        assertThat(method.typeParameters()).hasSize(1);
        assertThat(method.isConstructor()).isFalse();
        assertThat(method.isStatic()).isFalse();
        assertThat(method.isAbstract()).isFalse();
        assertThat(method.isFinal()).isTrue();
        assertThat(method.modifiers()).isEqualTo(PUBLIC_MODIFIER);

        assertThat(parameter.kind()).isEqualTo(DeclarationInfo.Kind.PARAMETER);
        assertThat(parameter.asParameter()).isSameAs(parameter);
        assertThat(parameter.name()).isEqualTo("fallback");
        assertThat(parameter.type()).isSameAs(intType);
        assertThat(parameter.declaringMethod()).isSameAs(method);

        assertThat(component.kind()).isEqualTo(DeclarationInfo.Kind.RECORD_COMPONENT);
        assertThat(component.asRecordComponent()).isSameAs(component);
        assertThat(component.name()).isEqualTo("count");
        assertThat(component.type()).isSameAs(intType);
        assertThat(component.field()).isSameAs(field);
        assertThat(component.accessor()).isSameAs(method);
        assertThat(component.declaringRecord()).isSameAs(owner);
    }

    @Test
    void classInfoModelsTypeParametersAndHierarchy() {
        FakeClassInfo numberDeclaration = new FakeClassInfo("java.lang.Number", "Number");
        FakeClassType numberType = new FakeClassType(numberDeclaration);
        FakeTypeVariable valueParameter = new FakeTypeVariable("V", List.of(numberType));
        FakeClassInfo comparableDeclaration = new FakeClassInfo("java.lang.Comparable", "Comparable");
        FakeClassInfo serializableDeclaration = new FakeClassInfo("java.io.Serializable", "Serializable");
        FakeClassInfo modelDeclaration = new FakeClassInfo("example.Model", "Model");
        FakeParameterizedType comparableOfModel = new FakeParameterizedType(
                new FakeClassType(comparableDeclaration), List.of(new FakeClassType(modelDeclaration)));
        FakeClassType serializableType = new FakeClassType(serializableDeclaration);
        FakeClassInfo derivedDeclaration = new FakeClassInfo("example.Derived", "Derived");
        derivedDeclaration.typeParameters = List.of(valueParameter);
        derivedDeclaration.superClass = numberType;
        derivedDeclaration.superClassDeclaration = numberDeclaration;
        derivedDeclaration.superInterfaces = List.of(comparableOfModel, serializableType);
        derivedDeclaration.superInterfacesDeclarations = List.of(comparableDeclaration, serializableDeclaration);

        assertThat(derivedDeclaration.typeParameters()).containsExactly(valueParameter);
        assertThat(derivedDeclaration.superClass()).isSameAs(numberType);
        assertThat(derivedDeclaration.superClassDeclaration()).isSameAs(numberDeclaration);
        assertThat(derivedDeclaration.superInterfaces()).containsExactly(comparableOfModel, serializableType);
        assertThat(derivedDeclaration.superInterfacesDeclarations())
                .containsExactly(comparableDeclaration, serializableDeclaration);
    }

    @Test
    void annotationInfoDefaultsDelegateToDeclarationAndValueMember() {
        FakeClassInfo annotationDeclaration = new FakeClassInfo("example.Config", "Config");
        FakeAnnotationInfo repeatableMarker = new FakeAnnotationInfo(
                new FakeClassInfo(Repeatable.class.getName(), "Repeatable"), Map.of());
        annotationDeclaration.addAnnotation(repeatableMarker);
        AnnotationMember value = new FakeAnnotationMember(AnnotationMember.Kind.STRING, "primary");
        AnnotationMember flag = new FakeAnnotationMember(AnnotationMember.Kind.BOOLEAN, Boolean.TRUE);
        FakeAnnotationInfo annotationInfo = new FakeAnnotationInfo(annotationDeclaration, Map.of(
                AnnotationMember.VALUE, value,
                "enabled", flag));

        assertThat(annotationInfo.name()).isEqualTo("example.Config");
        assertThat(annotationInfo.isRepeatable()).isTrue();
        assertThat(annotationInfo.hasValue()).isTrue();
        assertThat(annotationInfo.value()).isSameAs(value);
        assertThat(annotationInfo.hasMember("enabled")).isTrue();
        assertThat(annotationInfo.member("enabled")).isSameAs(flag);
        assertThat(annotationInfo.members())
                .containsEntry("enabled", flag)
                .containsEntry(AnnotationMember.VALUE, value);

        FakeAnnotationInfo noValue = new FakeAnnotationInfo(annotationDeclaration, Map.of("enabled", flag));
        assertThat(noValue.hasValue()).isFalse();
        assertThat(noValue.value()).isNull();
    }

    @Test
    void annotationMemberDefaultMethodsClassifyEveryKindAndExposeValues() {
        FakeClassInfo enumDeclaration = new FakeClassInfo(SampleMode.class.getName(), "SampleMode");
        FakePrimitiveType classValue = new FakePrimitiveType("int", PrimitiveType.PrimitiveKind.INT);
        FakeAnnotationInfo nested = new FakeAnnotationInfo(new FakeClassInfo("example.Nested", "Nested"), Map.of());
        List<AnnotationMember> arrayValue = List.of(new FakeAnnotationMember(AnnotationMember.Kind.STRING, "a"));
        Map<AnnotationMember.Kind, FakeAnnotationMember> members = Map.ofEntries(
                Map.entry(AnnotationMember.Kind.BOOLEAN, new FakeAnnotationMember(AnnotationMember.Kind.BOOLEAN, true)),
                Map.entry(AnnotationMember.Kind.BYTE, new FakeAnnotationMember(AnnotationMember.Kind.BYTE, (byte) 7)),
                Map.entry(
                        AnnotationMember.Kind.SHORT,
                        new FakeAnnotationMember(AnnotationMember.Kind.SHORT, (short) 11)),
                Map.entry(AnnotationMember.Kind.INT, new FakeAnnotationMember(AnnotationMember.Kind.INT, 13)),
                Map.entry(AnnotationMember.Kind.LONG, new FakeAnnotationMember(AnnotationMember.Kind.LONG, 17L)),
                Map.entry(AnnotationMember.Kind.FLOAT, new FakeAnnotationMember(AnnotationMember.Kind.FLOAT, 1.5F)),
                Map.entry(AnnotationMember.Kind.DOUBLE, new FakeAnnotationMember(AnnotationMember.Kind.DOUBLE, 2.5D)),
                Map.entry(AnnotationMember.Kind.CHAR, new FakeAnnotationMember(AnnotationMember.Kind.CHAR, 'x')),
                Map.entry(AnnotationMember.Kind.STRING, new FakeAnnotationMember(AnnotationMember.Kind.STRING, "text")),
                Map.entry(AnnotationMember.Kind.ENUM,
                        new FakeAnnotationMember(AnnotationMember.Kind.ENUM, enumDeclaration, "FAST")),
                Map.entry(
                        AnnotationMember.Kind.CLASS,
                        new FakeAnnotationMember(AnnotationMember.Kind.CLASS, classValue)),
                Map.entry(AnnotationMember.Kind.NESTED_ANNOTATION,
                        new FakeAnnotationMember(AnnotationMember.Kind.NESTED_ANNOTATION, nested)),
                Map.entry(
                        AnnotationMember.Kind.ARRAY,
                        new FakeAnnotationMember(AnnotationMember.Kind.ARRAY, arrayValue)));

        for (AnnotationMember.Kind kind : AnnotationMember.Kind.values()) {
            AnnotationMember member = members.get(kind);

            assertThat(member.isBoolean()).isEqualTo(kind == AnnotationMember.Kind.BOOLEAN);
            assertThat(member.isByte()).isEqualTo(kind == AnnotationMember.Kind.BYTE);
            assertThat(member.isShort()).isEqualTo(kind == AnnotationMember.Kind.SHORT);
            assertThat(member.isInt()).isEqualTo(kind == AnnotationMember.Kind.INT);
            assertThat(member.isLong()).isEqualTo(kind == AnnotationMember.Kind.LONG);
            assertThat(member.isFloat()).isEqualTo(kind == AnnotationMember.Kind.FLOAT);
            assertThat(member.isDouble()).isEqualTo(kind == AnnotationMember.Kind.DOUBLE);
            assertThat(member.isChar()).isEqualTo(kind == AnnotationMember.Kind.CHAR);
            assertThat(member.isString()).isEqualTo(kind == AnnotationMember.Kind.STRING);
            assertThat(member.isEnum()).isEqualTo(kind == AnnotationMember.Kind.ENUM);
            assertThat(member.isClass()).isEqualTo(kind == AnnotationMember.Kind.CLASS);
            assertThat(member.isNestedAnnotation()).isEqualTo(kind == AnnotationMember.Kind.NESTED_ANNOTATION);
            assertThat(member.isArray()).isEqualTo(kind == AnnotationMember.Kind.ARRAY);
        }

        assertThat(members.get(AnnotationMember.Kind.BOOLEAN).asBoolean()).isTrue();
        assertThat(members.get(AnnotationMember.Kind.BYTE).asByte()).isEqualTo((byte) 7);
        assertThat(members.get(AnnotationMember.Kind.SHORT).asShort()).isEqualTo((short) 11);
        assertThat(members.get(AnnotationMember.Kind.INT).asInt()).isEqualTo(13);
        assertThat(members.get(AnnotationMember.Kind.LONG).asLong()).isEqualTo(17L);
        assertThat(members.get(AnnotationMember.Kind.FLOAT).asFloat()).isEqualTo(1.5F);
        assertThat(members.get(AnnotationMember.Kind.DOUBLE).asDouble()).isEqualTo(2.5D);
        assertThat(members.get(AnnotationMember.Kind.CHAR).asChar()).isEqualTo('x');
        assertThat(members.get(AnnotationMember.Kind.STRING).asString()).isEqualTo("text");
        assertThat(members.get(AnnotationMember.Kind.ENUM).asEnum(SampleMode.class)).isEqualTo(SampleMode.FAST);
        assertThat(members.get(AnnotationMember.Kind.ENUM).asEnumClass()).isSameAs(enumDeclaration);
        assertThat(members.get(AnnotationMember.Kind.ENUM).asEnumConstant()).isEqualTo("FAST");
        assertThat(members.get(AnnotationMember.Kind.CLASS).asType()).isSameAs(classValue);
        assertThat(members.get(AnnotationMember.Kind.NESTED_ANNOTATION).asNestedAnnotation()).isSameAs(nested);
        assertThat(members.get(AnnotationMember.Kind.ARRAY).asArray()).containsExactlyElementsOf(arrayValue);
    }

    @Test
    void typeUseAnnotationsAreScopedToParameterizedTypesAndTypeArguments() {
        FakeClassInfo markerDeclaration = new FakeClassInfo(TypeUseMarker.class.getName(), "TypeUseMarker");
        FakeAnnotationInfo containerAnnotation = new FakeAnnotationInfo(
                markerDeclaration,
                Map.of(AnnotationMember.VALUE, new FakeAnnotationMember(AnnotationMember.Kind.STRING, "container")));
        FakeAnnotationInfo elementAnnotation = new FakeAnnotationInfo(
                markerDeclaration,
                Map.of(AnnotationMember.VALUE, new FakeAnnotationMember(AnnotationMember.Kind.STRING, "element")));
        FakeClassType rawList = new FakeClassType(new FakeClassInfo(List.class.getName(), "List"));
        FakeClassType stringElement = new FakeClassType(new FakeClassInfo(String.class.getName(), "String"));
        FakeParameterizedType annotatedList = new FakeParameterizedType(rawList, List.of(stringElement));
        annotatedList.addAnnotation(containerAnnotation);
        stringElement.addAnnotation(elementAnnotation);

        assertThat(annotatedList.annotation(TypeUseMarker.class)).isSameAs(containerAnnotation);
        assertThat(annotatedList.annotation(TypeUseMarker.class).value().asString()).isEqualTo("container");
        assertThat(rawList.hasAnnotation(TypeUseMarker.class)).isFalse();
        assertThat(annotatedList.typeArguments()).singleElement().satisfies(typeArgument -> {
            assertThat(typeArgument.annotation(TypeUseMarker.class)).isSameAs(elementAnnotation);
            assertThat(typeArgument.annotation(TypeUseMarker.class).value().asString()).isEqualTo("element");
        });
        assertThat(annotatedList.annotations(annotation -> annotation.value().asString().startsWith("contain")))
                .containsExactly(containerAnnotation);
    }

    @Test
    void annotationTargetQueriesAnnotationsByTypeAndPredicate() {
        FakeClassInfo target = new FakeClassInfo("example.Target", "Target");
        FakeAnnotationInfo marker = new FakeAnnotationInfo(
                new FakeClassInfo(Marker.class.getName(), "Marker"), Map.of());
        FakeAnnotationInfo repeatedOne = new FakeAnnotationInfo(
                new FakeClassInfo(Repeated.class.getName(), "Repeated"),
                Map.of(AnnotationMember.VALUE, new FakeAnnotationMember(AnnotationMember.Kind.STRING, "one")));
        FakeAnnotationInfo repeatedTwo = new FakeAnnotationInfo(
                new FakeClassInfo(Repeated.class.getName(), "Repeated"),
                Map.of(AnnotationMember.VALUE, new FakeAnnotationMember(AnnotationMember.Kind.STRING, "two")));
        target.addAnnotation(marker);
        target.addAnnotation(repeatedOne);
        target.addAnnotation(repeatedTwo);

        assertThat(target.hasAnnotation(Marker.class)).isTrue();
        assertThat(target.hasAnnotation(Repeated.class)).isTrue();
        assertThat(target.hasAnnotation(Deprecated.class)).isFalse();
        assertThat(target.hasAnnotation(annotation -> annotation.name().endsWith("Repeated"))).isTrue();
        assertThat(target.annotation(Marker.class)).isSameAs(marker);
        assertThat(target.repeatableAnnotation(Repeated.class)).containsExactly(repeatedOne, repeatedTwo);
        assertThat(target.annotations(annotation -> annotation.hasValue())).containsExactly(repeatedOne, repeatedTwo);
        assertThat(target.annotations()).containsExactly(marker, repeatedOne, repeatedTwo);
    }

    private enum SampleMode {
        FAST
    }

    private @interface Marker {}

    @Target(ElementType.TYPE_USE)
    private @interface TypeUseMarker {
        String value();
    }

    @Repeatable(RepeatedContainer.class)
    private @interface Repeated {
        String value();
    }

    private @interface RepeatedContainer {
        Repeated[] value();
    }

    private abstract static class AnnotatedElementSupport {
        private final List<AnnotationInfo> annotations = new ArrayList<>();

        final void addAnnotation(AnnotationInfo annotation) {
            annotations.add(annotation);
        }

        public final boolean hasAnnotation(Class<? extends Annotation> annotationType) {
            return hasAnnotation(annotation -> annotation.name().equals(annotationType.getName()));
        }

        public final boolean hasAnnotation(Predicate<AnnotationInfo> predicate) {
            return annotations.stream().anyMatch(predicate);
        }

        public final <T extends Annotation> AnnotationInfo annotation(Class<T> annotationType) {
            return annotations.stream()
                    .filter(annotation -> annotation.name().equals(annotationType.getName()))
                    .findFirst()
                    .orElse(null);
        }

        public final <T extends Annotation> Collection<AnnotationInfo> repeatableAnnotation(Class<T> annotationType) {
            return annotations(annotation -> annotation.name().equals(annotationType.getName()));
        }

        public final Collection<AnnotationInfo> annotations(Predicate<AnnotationInfo> predicate) {
            return annotations.stream().filter(predicate).toList();
        }

        public final Collection<AnnotationInfo> annotations() {
            return List.copyOf(annotations);
        }
    }

    private static final class GenericType extends AnnotatedElementSupport implements Type {
        private final Type.Kind kind;

        private GenericType(Type.Kind kind) {
            this.kind = kind;
        }

        @Override
        public Type.Kind kind() {
            return kind;
        }
    }

    private static final class GenericDeclaration extends AnnotatedElementSupport implements DeclarationInfo {
        private final DeclarationInfo.Kind kind;

        private GenericDeclaration(DeclarationInfo.Kind kind) {
            this.kind = kind;
        }

        @Override
        public DeclarationInfo.Kind kind() {
            return kind;
        }
    }

    private static final class FakeVoidType extends AnnotatedElementSupport implements VoidType {
        private final String name;

        private FakeVoidType(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }
    }

    private static final class FakePrimitiveType extends AnnotatedElementSupport implements PrimitiveType {
        private final String name;
        private final PrimitiveType.PrimitiveKind primitiveKind;

        private FakePrimitiveType(String name, PrimitiveType.PrimitiveKind primitiveKind) {
            this.name = name;
            this.primitiveKind = primitiveKind;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public PrimitiveType.PrimitiveKind primitiveKind() {
            return primitiveKind;
        }
    }

    private static final class FakeClassType extends AnnotatedElementSupport implements ClassType {
        private final ClassInfo declaration;

        private FakeClassType(ClassInfo declaration) {
            this.declaration = declaration;
        }

        @Override
        public ClassInfo declaration() {
            return declaration;
        }
    }

    private static final class FakeArrayType extends AnnotatedElementSupport implements ArrayType {
        private final Type componentType;

        private FakeArrayType(Type componentType) {
            this.componentType = componentType;
        }

        @Override
        public Type componentType() {
            return componentType;
        }
    }

    private static final class FakeParameterizedType extends AnnotatedElementSupport implements ParameterizedType {
        private final ClassType genericClass;
        private final List<Type> typeArguments;

        private FakeParameterizedType(ClassType genericClass, List<Type> typeArguments) {
            this.genericClass = genericClass;
            this.typeArguments = typeArguments;
        }

        @Override
        public ClassType genericClass() {
            return genericClass;
        }

        @Override
        public List<Type> typeArguments() {
            return typeArguments;
        }
    }

    private static final class FakeTypeVariable extends AnnotatedElementSupport implements TypeVariable {
        private final String name;
        private final List<Type> bounds;

        private FakeTypeVariable(String name, List<Type> bounds) {
            this.name = name;
            this.bounds = bounds;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public List<Type> bounds() {
            return bounds;
        }
    }

    private static final class FakeWildcardType extends AnnotatedElementSupport implements WildcardType {
        private final Type upperBound;
        private final Type lowerBound;

        private FakeWildcardType(Type upperBound, Type lowerBound) {
            this.upperBound = upperBound;
            this.lowerBound = lowerBound;
        }

        @Override
        public Type upperBound() {
            return upperBound;
        }

        @Override
        public Type lowerBound() {
            return lowerBound;
        }
    }

    private static final class FakePackageInfo extends AnnotatedElementSupport implements PackageInfo {
        private final String name;

        private FakePackageInfo(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }
    }

    private static final class FakeClassInfo extends AnnotatedElementSupport implements ClassInfo {
        private final String name;
        private final String simpleName;
        private PackageInfo packageInfo;
        private List<TypeVariable> typeParameters = List.of();
        private Type superClass;
        private ClassInfo superClassDeclaration;
        private List<Type> superInterfaces = List.of();
        private List<ClassInfo> superInterfacesDeclarations = List.of();
        private boolean plainClass;
        private boolean interfaceClass;
        private boolean enumClass;
        private boolean annotationClass;
        private boolean recordClass;
        private boolean abstractClass;
        private boolean finalClass;
        private int modifiers;
        private Collection<MethodInfo> constructors = List.of();
        private Collection<MethodInfo> methods = List.of();
        private Collection<FieldInfo> fields = List.of();
        private Collection<RecordComponentInfo> recordComponents = List.of();

        private FakeClassInfo(String name, String simpleName) {
            this.name = name;
            this.simpleName = simpleName;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String simpleName() {
            return simpleName;
        }

        @Override
        public PackageInfo packageInfo() {
            return packageInfo;
        }

        @Override
        public List<TypeVariable> typeParameters() {
            return typeParameters;
        }

        @Override
        public Type superClass() {
            return superClass;
        }

        @Override
        public ClassInfo superClassDeclaration() {
            return superClassDeclaration;
        }

        @Override
        public List<Type> superInterfaces() {
            return superInterfaces;
        }

        @Override
        public List<ClassInfo> superInterfacesDeclarations() {
            return superInterfacesDeclarations;
        }

        @Override
        public boolean isPlainClass() {
            return plainClass;
        }

        @Override
        public boolean isInterface() {
            return interfaceClass;
        }

        @Override
        public boolean isEnum() {
            return enumClass;
        }

        @Override
        public boolean isAnnotation() {
            return annotationClass;
        }

        @Override
        public boolean isRecord() {
            return recordClass;
        }

        @Override
        public boolean isAbstract() {
            return abstractClass;
        }

        @Override
        public boolean isFinal() {
            return finalClass;
        }

        @Override
        public int modifiers() {
            return modifiers;
        }

        @Override
        public Collection<MethodInfo> constructors() {
            return constructors;
        }

        @Override
        public Collection<MethodInfo> methods() {
            return methods;
        }

        @Override
        public Collection<FieldInfo> fields() {
            return fields;
        }

        @Override
        public Collection<RecordComponentInfo> recordComponents() {
            return recordComponents;
        }
    }

    private static final class FakeFieldInfo extends AnnotatedElementSupport implements FieldInfo {
        private final String name;
        private final Type type;
        private final ClassInfo declaringClass;
        private final boolean staticField;
        private final boolean finalField;
        private final int modifiers;

        private FakeFieldInfo(
                String name,
                Type type,
                ClassInfo declaringClass,
                boolean staticField,
                boolean finalField,
                int modifiers) {
            this.name = name;
            this.type = type;
            this.declaringClass = declaringClass;
            this.staticField = staticField;
            this.finalField = finalField;
            this.modifiers = modifiers;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Type type() {
            return type;
        }

        @Override
        public boolean isStatic() {
            return staticField;
        }

        @Override
        public boolean isFinal() {
            return finalField;
        }

        @Override
        public int modifiers() {
            return modifiers;
        }

        @Override
        public ClassInfo declaringClass() {
            return declaringClass;
        }
    }

    private static final class FakeMethodInfo extends AnnotatedElementSupport implements MethodInfo {
        private final String name;
        private final Type returnType;
        private final ClassInfo declaringClass;
        private final boolean constructor;
        private final boolean staticMethod;
        private final boolean abstractMethod;
        private final boolean finalMethod;
        private final int modifiers;
        private List<ParameterInfo> parameters = List.of();
        private Type receiverType;
        private List<Type> throwsTypes = List.of();
        private List<TypeVariable> typeParameters = List.of();

        private FakeMethodInfo(
                String name,
                Type returnType,
                ClassInfo declaringClass,
                boolean constructor,
                boolean staticMethod,
                boolean abstractMethod,
                boolean finalMethod,
                int modifiers) {
            this.name = name;
            this.returnType = returnType;
            this.declaringClass = declaringClass;
            this.constructor = constructor;
            this.staticMethod = staticMethod;
            this.abstractMethod = abstractMethod;
            this.finalMethod = finalMethod;
            this.modifiers = modifiers;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public List<ParameterInfo> parameters() {
            return parameters;
        }

        @Override
        public Type returnType() {
            return returnType;
        }

        @Override
        public Type receiverType() {
            return receiverType;
        }

        @Override
        public List<Type> throwsTypes() {
            return throwsTypes;
        }

        @Override
        public List<TypeVariable> typeParameters() {
            return typeParameters;
        }

        @Override
        public boolean isConstructor() {
            return constructor;
        }

        @Override
        public boolean isStatic() {
            return staticMethod;
        }

        @Override
        public boolean isAbstract() {
            return abstractMethod;
        }

        @Override
        public boolean isFinal() {
            return finalMethod;
        }

        @Override
        public int modifiers() {
            return modifiers;
        }

        @Override
        public ClassInfo declaringClass() {
            return declaringClass;
        }
    }

    private static final class FakeParameterInfo extends AnnotatedElementSupport implements ParameterInfo {
        private final String name;
        private final Type type;
        private final MethodInfo declaringMethod;

        private FakeParameterInfo(String name, Type type, MethodInfo declaringMethod) {
            this.name = name;
            this.type = type;
            this.declaringMethod = declaringMethod;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Type type() {
            return type;
        }

        @Override
        public MethodInfo declaringMethod() {
            return declaringMethod;
        }
    }

    private static final class FakeRecordComponentInfo extends AnnotatedElementSupport implements RecordComponentInfo {
        private final String name;
        private final Type type;
        private final FieldInfo field;
        private final MethodInfo accessor;
        private final ClassInfo declaringRecord;

        private FakeRecordComponentInfo(
                String name, Type type, FieldInfo field, MethodInfo accessor, ClassInfo declaringRecord) {
            this.name = name;
            this.type = type;
            this.field = field;
            this.accessor = accessor;
            this.declaringRecord = declaringRecord;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Type type() {
            return type;
        }

        @Override
        public FieldInfo field() {
            return field;
        }

        @Override
        public MethodInfo accessor() {
            return accessor;
        }

        @Override
        public ClassInfo declaringRecord() {
            return declaringRecord;
        }
    }

    private static final class FakeAnnotationInfo implements AnnotationInfo {
        private final ClassInfo declaration;
        private final Map<String, AnnotationMember> members;

        private FakeAnnotationInfo(ClassInfo declaration, Map<String, AnnotationMember> members) {
            this.declaration = declaration;
            this.members = members;
        }

        @Override
        public ClassInfo declaration() {
            return declaration;
        }

        @Override
        public boolean hasMember(String name) {
            return members.containsKey(name);
        }

        @Override
        public AnnotationMember member(String name) {
            return members.get(name);
        }

        @Override
        public Map<String, AnnotationMember> members() {
            return members;
        }
    }

    private static final class FakeAnnotationMember implements AnnotationMember {
        private final AnnotationMember.Kind kind;
        private final Object value;
        private final ClassInfo enumClass;
        private final String enumConstant;

        private FakeAnnotationMember(AnnotationMember.Kind kind, Object value) {
            this(kind, value, null);
        }

        private FakeAnnotationMember(AnnotationMember.Kind kind, ClassInfo enumClass, String enumConstant) {
            this(kind, enumConstant, enumClass);
        }

        private FakeAnnotationMember(AnnotationMember.Kind kind, Object value, ClassInfo enumClass) {
            this.kind = kind;
            this.value = value;
            this.enumClass = enumClass;
            this.enumConstant = enumClass == null ? null : (String) value;
        }

        @Override
        public AnnotationMember.Kind kind() {
            return kind;
        }

        @Override
        public boolean asBoolean() {
            return (Boolean) value;
        }

        @Override
        public byte asByte() {
            return (Byte) value;
        }

        @Override
        public short asShort() {
            return (Short) value;
        }

        @Override
        public int asInt() {
            return (Integer) value;
        }

        @Override
        public long asLong() {
            return (Long) value;
        }

        @Override
        public float asFloat() {
            return (Float) value;
        }

        @Override
        public double asDouble() {
            return (Double) value;
        }

        @Override
        public char asChar() {
            return (Character) value;
        }

        @Override
        public String asString() {
            return (String) value;
        }

        @Override
        public <E extends Enum<E>> E asEnum(Class<E> enumType) {
            return Enum.valueOf(enumType, enumConstant);
        }

        @Override
        public ClassInfo asEnumClass() {
            return enumClass;
        }

        @Override
        public String asEnumConstant() {
            return enumConstant;
        }

        @Override
        public Type asType() {
            return (Type) value;
        }

        @Override
        public AnnotationInfo asNestedAnnotation() {
            return (AnnotationInfo) value;
        }

        @Override
        @SuppressWarnings("unchecked")
        public List<AnnotationMember> asArray() {
            return (List<AnnotationMember>) value;
        }
    }
}
