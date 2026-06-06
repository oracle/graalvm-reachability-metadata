/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_model_utils;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.model.ClassRef;
import io.sundr.model.Kind;
import io.sundr.model.Method;
import io.sundr.model.Property;
import io.sundr.model.RichTypeDef;
import io.sundr.model.TypeDef;
import io.sundr.model.TypeDefBuilder;
import io.sundr.model.TypeParamDef;
import io.sundr.model.TypeRef;
import io.sundr.model.functions.Assignable;
import io.sundr.model.functions.GetDefinition;
import io.sundr.model.functions.TypeCast;
import io.sundr.model.repo.DefinitionRepository;
import io.sundr.model.utils.Collections;
import io.sundr.model.utils.Getter;
import io.sundr.model.utils.Optionals;
import io.sundr.model.utils.Parsers;
import io.sundr.model.utils.Record;
import io.sundr.model.utils.Setter;
import io.sundr.model.utils.TypeArguments;
import io.sundr.model.utils.Types;
import io.sundr.model.visitors.ReplacePackage;
import io.sundr.model.visitors.ReplaceType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class Sundr_model_utilsTest {
    @Test
    void recognizesPrimitiveCollectionMapAndOptionalReferences() {
        TypeRef boxedInteger = Types.box(Types.PRIMITIVE_INT_REF);
        ClassRef listOfStrings = Collections.LIST.toReference(Types.STRING_REF);
        ClassRef mapOfStringToInteger = Collections.MAP.toReference(Types.STRING_REF, boxedInteger);
        DefinitionRepository repository = DefinitionRepository.getRepository();
        repository.register(Collections.COLLECTION);
        repository.register(Collections.LIST);
        repository.register(Collections.MAP);
        ClassRef optionalString = Optionals.OPTIONAL.toReference(Types.STRING_REF);

        assertThat(boxedInteger).isEqualTo(Types.INT_REF);
        assertThat(Types.isPrimitive(Types.PRIMITIVE_INT_REF)).isTrue();
        assertThat(Types.isPrimitive(boxedInteger)).isFalse();

        assertThat(Types.isList(listOfStrings)).isTrue();
        assertThat(Collections.isCollection(listOfStrings)).isTrue();
        assertThat(Collections.getCollectionElementType(listOfStrings)).contains(Types.STRING_REF);

        assertThat(Types.isMap(mapOfStringToInteger)).isTrue();
        assertThat(Collections.getMapKeyType(mapOfStringToInteger)).contains(Types.STRING_REF);
        assertThat(Collections.getMapValueType(mapOfStringToInteger)).contains(boxedInteger);

        assertThat(Types.isOptional(optionalString)).isTrue();
        assertThat(Optionals.isOptional(optionalString)).isTrue();
        assertThat(Optionals.isOptionalInt(Optionals.OPTIONAL_INT.toReference())).isTrue();
        assertThat(Optionals.isOptionalDouble(Optionals.OPTIONAL_DOUBLE.toReference())).isTrue();
        assertThat(Optionals.isOptionalLong(Optionals.OPTIONAL_LONG.toReference())).isTrue();
    }

    @Test
    void parsesImportsNamesAndMethodBodiesFromJavaSourceText() {
        String source = """
                package example.parser;

                import java.util.List;
                import java.util.Map;

                public class Greeter {
                    public String greet(String name) {
                        String prefix = "Hello, ";
                        return prefix + name;
                    }

                    public int size(List values) {
                        return values.size();
                    }
                }
                """;
        Method greet = Method.newMethod(
                "greet",
                Types.STRING_REF,
                Property.newProperty(Types.STRING_REF, "name"));

        List<ClassRef> imports = Parsers.parseImports(source);
        String greetBody = Parsers.parseMethodBody(source, greet);
        String sizeBody = Parsers.parseMethodBody(
                source,
                "size",
                List.of(Property.newProperty(Collections.LIST.toReference(Types.STRING_REF), "values")));

        assertThat(imports)
                .extracting(ClassRef::getFullyQualifiedName)
                .containsExactly("java.util.List", "java.util.Map");
        assertThat(greetBody).contains("String prefix = \"Hello, \";").contains("return prefix + name;");
        assertThat(sizeBody).contains("return values.size();");
        assertThat(Types.parsePackage(source)).contains("example.parser");
        assertThat(Types.parseName(source)).contains("Greeter");
        assertThat(Types.parseFullyQualifiedName(source)).isEqualTo("example.parser.Greeter");
    }

    @Test
    void resolvesDefinitionsHierarchyPropertiesAndAssignability() {
        TypeDef identifiable = new TypeDefBuilder()
                .withKind(Kind.INTERFACE)
                .withPackageName("example.model")
                .withName("Identifiable")
                .addToMethods(Method.newMethod("id", Types.STRING_REF))
                .build();
        TypeDef base = new TypeDefBuilder()
                .withKind(Kind.CLASS)
                .withPackageName("example.model")
                .withName("Base")
                .addToProperties(Property.newProperty(Types.STRING_REF, "name"))
                .build();
        TypeDef child = new TypeDefBuilder()
                .withKind(Kind.CLASS)
                .withPackageName("example.model")
                .withName("Child")
                .addToExtendsList(base.toReference())
                .addToImplementsList(identifiable.toReference())
                .addToProperties(Property.newProperty(Types.INT_REF, "count"))
                .addToMethods(Method.newMethod("count", Types.INT_REF))
                .build();

        DefinitionRepository repository = DefinitionRepository.getRepository();
        repository.register(identifiable);
        repository.register(base);
        repository.register(child);

        assertThat(GetDefinition.of(child.toReference())).isEqualTo(child);
        assertThat(Assignable.isAssignable(base).from(child)).isTrue();
        assertThat(Assignable.isAssignable(identifiable).from(child)).isTrue();
        assertThat(Assignable.isAssignable(Types.PRIMITIVE_INT_REF).from(Types.INT_REF)).isTrue();
        assertThat(Types.hasProperty(child, "count")).isTrue();
        assertThat(Types.hasMethod(child, "count")).isTrue();
        assertThat(Types.allProperties(child)).extracting(Property::getName).contains("name", "count");
        assertThat(Types.unrollHierarchy(child)).extracting(TypeDef::getFullyQualifiedName)
                .contains("example.model.Base", "example.model.Child");
    }

    @Test
    void mapsGenericTypeArgumentsToConcreteReferences() {
        TypeParamDef key = Types.newTypeParamDef("K");
        TypeParamDef value = Types.newTypeParamDef("V");
        TypeDef pair = new TypeDefBuilder()
                .withKind(Kind.CLASS)
                .withPackageName("example.generics")
                .withName("Pair")
                .withParameters(key, value)
                .addToProperties(Property.newProperty(key.toReference(), "key"))
                .addToProperties(Property.newProperty(value.toReference(), "value"))
                .build();
        ClassRef pairOfStringAndInteger = pair.toReference(Types.STRING_REF, Types.INT_REF);
        DefinitionRepository.getRepository().register(pair);

        Map<String, TypeRef> mappings = TypeArguments.getGenericArgumentsMappings(pairOfStringAndInteger, pair);
        RichTypeDef richTypeDef = TypeArguments.apply(pairOfStringAndInteger);

        assertThat(mappings).containsEntry("K", Types.STRING_REF).containsEntry("V", Types.INT_REF);
        assertThat(richTypeDef.getFullyQualifiedName()).isEqualTo("example.generics.Pair");
        assertThat(richTypeDef.getAllProperties()).extracting(Property::getName).containsExactly("key", "value");
        assertThat(richTypeDef.getProperties()).extracting(Property::getTypeRef)
                .containsExactly(Types.STRING_REF, Types.INT_REF);
    }

    @Test
    void castsReferencesToInheritedGenericTypes() {
        TypeParamDef item = Types.newTypeParamDef("T");
        TypeDef source = new TypeDefBuilder()
                .withKind(Kind.INTERFACE)
                .withPackageName("example.cast")
                .withName("Source")
                .withParameters(item)
                .build();
        TypeDef stringSource = new TypeDefBuilder()
                .withKind(Kind.CLASS)
                .withPackageName("example.cast")
                .withName("StringSource")
                .addToImplementsList(source.toReference(Types.STRING_REF))
                .build();

        DefinitionRepository repository = DefinitionRepository.getRepository();
        repository.register(source);
        repository.register(stringSource);

        ClassRef castReference = TypeCast.to(source.toReference())
                .apply(stringSource.toReference())
                .orElseThrow();

        assertThat(castReference.getFullyQualifiedName()).isEqualTo("example.cast.Source");
        assertThat(castReference.getArguments()).containsExactly(Types.STRING_REF);
        assertThat(TypeCast.to(source.toReference()).apply(Types.PRIMITIVE_INT_REF)).isEmpty();
    }

    @Test
    void discoversPropertyAccessorsIncludingRecordComponents() {
        Property name = Property.newProperty(Types.STRING_REF, "name");
        Property enabled = Property.newProperty(Types.PRIMITIVE_BOOLEAN_REF, "enabled");
        Method getName = Method.newMethod("getName", Types.STRING_REF);
        Method setName = Method.newMethod("setName", Types.VOID, name);
        TypeDef bean = new TypeDefBuilder()
                .withKind(Kind.CLASS)
                .withPackageName("example.accessors")
                .withName("Bean")
                .addToProperties(name)
                .addToProperties(enabled)
                .addToMethods(getName)
                .addToMethods(setName)
                .build();
        TypeDef record = new TypeDefBuilder()
                .withKind(Kind.CLASS)
                .withPackageName("example.accessors")
                .withName("PersonRecord")
                .addToExtendsList(ClassRef.forName("java.lang.Record"))
                .addToProperties(name)
                .addToMethods(Method.newMethod("name", Types.STRING_REF))
                .build();

        Method generatedBooleanGetter = Getter.forProperty(enabled);

        assertThat(Getter.name(name)).isEqualTo("getName");
        assertThat(Getter.name(enabled)).isEqualTo("isEnabled");
        assertThat(generatedBooleanGetter.getName()).isEqualTo("isEnabled");
        assertThat(generatedBooleanGetter.getReturnType()).isEqualTo(Types.PRIMITIVE_BOOLEAN_REF);
        assertThat(Getter.is(generatedBooleanGetter)).isTrue();
        assertThat(Getter.propertyName(getName)).isEqualTo("name");
        assertThat(Getter.find(bean, name)).isEqualTo(getName);
        assertThat(Getter.findOptional(bean, Property.newProperty(Types.STRING_REF, "missing"))).isEmpty();

        assertThat(Setter.has(bean, name)).isTrue();
        assertThat(Setter.find(bean, name).getSignature()).isEqualTo(setName.getSignature());
        assertThat(Setter.isApplicable(setName, name)).isTrue();

        assertThat(Record.is(record)).isTrue();
        assertThat(Getter.find(record, name)).isEqualTo(record.getMethods().get(0));
    }

    @Test
    void visitorsRewritePackagesAndClassReferencesAcrossBuilders() {
        ClassRef oldWidgetRef = ClassRef.forName("example.old.Widget");
        ClassRef newWidgetRef = ClassRef.forName("example.new.Widget");
        TypeDef original = new TypeDefBuilder()
                .withKind(Kind.CLASS)
                .withPackageName("example.old")
                .withName("Container")
                .addToProperties(Property.newProperty(oldWidgetRef, "widget"))
                .addToMethods(Method.newMethod("widget", oldWidgetRef))
                .build();

        TypeDef moved = new TypeDefBuilder(original)
                .accept(new ReplacePackage("example.old", "example.new"))
                .build();
        TypeDef retargeted = new TypeDefBuilder(moved)
                .accept(new ReplaceType(newWidgetRef, Types.STRING_REF))
                .build();

        assertThat(moved.getPackageName()).isEqualTo("example.new");
        assertThat(moved.getProperties()).extracting(property -> property.getTypeRef().toString())
                .containsExactly("example.new.Widget");
        assertThat(moved.getMethods()).extracting(method -> method.getReturnType().toString())
                .containsExactly("example.new.Widget");

        assertThat(retargeted.getProperties()).extracting(property -> property.getTypeRef().toString())
                .containsExactly("java.lang.String");
        assertThat(retargeted.getMethods()).extracting(method -> method.getReturnType().toString())
                .containsExactly("java.lang.String");
    }
}
