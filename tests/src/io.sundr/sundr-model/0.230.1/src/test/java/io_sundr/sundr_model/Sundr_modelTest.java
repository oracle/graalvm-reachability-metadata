/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_model;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.model.AnnotationRef;
import io.sundr.model.AnnotationRefBuilder;
import io.sundr.model.Assign;
import io.sundr.model.AttributeKey;
import io.sundr.model.Block;
import io.sundr.model.Break;
import io.sundr.model.ClassRef;
import io.sundr.model.ClassRefBuilder;
import io.sundr.model.Construct;
import io.sundr.model.Continue;
import io.sundr.model.Declare;
import io.sundr.model.Do;
import io.sundr.model.Empty;
import io.sundr.model.Expression;
import io.sundr.model.For;
import io.sundr.model.Foreach;
import io.sundr.model.GreaterThan;
import io.sundr.model.If;
import io.sundr.model.Kind;
import io.sundr.model.Lambda;
import io.sundr.model.Method;
import io.sundr.model.MethodBuilder;
import io.sundr.model.MethodCall;
import io.sundr.model.Modifiers;
import io.sundr.model.NewArray;
import io.sundr.model.PostIncrement;
import io.sundr.model.PrimitiveRef;
import io.sundr.model.Property;
import io.sundr.model.PropertyBuilder;
import io.sundr.model.PropertyRef;
import io.sundr.model.Return;
import io.sundr.model.StringStatement;
import io.sundr.model.Switch;
import io.sundr.model.Synchronized;
import io.sundr.model.Ternary;
import io.sundr.model.Throw;
import io.sundr.model.Try;
import io.sundr.model.TypeDef;
import io.sundr.model.TypeDefBuilder;
import io.sundr.model.TypeParamDef;
import io.sundr.model.TypeParamDefBuilder;
import io.sundr.model.ValueRef;
import io.sundr.model.While;
import io.sundr.model.WildcardRef;
import io.sundr.model.WildcardRefBuilder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class Sundr_modelTest {

    private static final PrimitiveRef INT = new PrimitiveRef("int", 0, Map.of());
    private static final ClassRef STRING = ClassRef.forName("java.lang.String");
    private static final ClassRef NUMBER = ClassRef.forName("java.lang.Number");
    private static final ClassRef SERIALIZABLE = ClassRef.forName("java.io.Serializable");

    @Test
    void buildsTypeDefinitionsWithNestedMembersAttributesAndAnnotations() {
        AttributeKey<String> sourceAttribute = new AttributeKey<>("source", String.class);
        TypeParamDef typeParameter = new TypeParamDefBuilder()
                .withName("T")
                .addToBounds(NUMBER)
                .addToAttributes(sourceAttribute, "type-parameter")
                .build();
        AnnotationRef generated = new AnnotationRefBuilder()
                .withClassRef(ClassRef.forName("example.Generated"))
                .addToParameters("value", "sundr-model-test")
                .build();
        Property value = new PropertyBuilder()
                .withModifiers(privateFinalModifiers())
                .withTypeRef(typeParameter.toReference())
                .withName("value")
                .withInitialValue(new ValueRef(42))
                .addToComments("stored constructor value")
                .addToAttributes(sourceAttribute, "property")
                .build();
        Method getter = new MethodBuilder()
                .withModifiers(publicModifiers())
                .withName("getValue")
                .withReturnType(typeParameter.toReference())
                .withBlock(new Block(Return.variable(value)))
                .addToComments("Returns the stored value.")
                .build();
        TypeDef nested = TypeDef.forName("example.model.Box.Metadata");

        TypeDef box = new TypeDefBuilder()
                .withKind(Kind.CLASS)
                .withPackageName("example.model")
                .withName("Box")
                .withModifiers(publicModifiers())
                .addToComments("A small generic value object.")
                .addToAnnotations(generated)
                .addToImplementsList(SERIALIZABLE)
                .addToParameters(typeParameter)
                .addToProperties(value)
                .addToMethods(getter)
                .addToInnerTypes(nested)
                .addToAttributes(sourceAttribute, "type")
                .build();

        assertThat(box.getFullyQualifiedName()).isEqualTo("example.model.Box");
        assertThat(box.isClass()).isTrue();
        assertThat(box.getAttribute(sourceAttribute)).isEqualTo("type");
        assertThat(box.getParameters()).containsExactly(typeParameter);
        assertThat(box.getProperties()).containsExactly(value);
        assertThat(box.getMethods()).containsExactly(getter);
        assertThat(box.getInnerTypes()).containsExactly(nested);
        assertThat(box.getAnnotations()).containsExactly(generated);
        assertThat(box.getImports()).contains("java.io.Serializable");
        assertThat(box.getReferences()).extracting(ClassRef::getFullyQualifiedName)
                .contains("java.io.Serializable");
        assertThat(typeParameter.getBounds()).containsExactly(NUMBER);

        String definition = box.renderDefinition();

        assertThat(definition)
                .contains("class Box")
                .contains("Serializable")
                .contains("T extends")
                .contains("Number");
    }

    @Test
    void fluentBuildersCanCopyEditMatchAndRemoveGeneratedModelParts() {
        Property first = Property.newProperty(STRING, "firstName");
        Property last = Property.newProperty(STRING, "lastName");
        Method fullName = Method.newMethod("fullName", STRING);
        TypeDef person = new TypeDefBuilder()
                .withKind(Kind.CLASS)
                .withPackageName("example.people")
                .withName("Person")
                .withProperties(first, last)
                .withMethods(fullName)
                .build();

        TypeDef edited = new TypeDefBuilder(person)
                .editMatchingProperty(property -> "firstName".equals(property.getName()))
                .withName("givenName")
                .endProperty()
                .editMatchingMethod(method -> "fullName".equals(method.getName()))
                .withName("displayName")
                .endMethod()
                .removeMatchingFromProperties(property -> "lastName".equals(property.getName()))
                .addNewPropertyLike(Property.newProperty(STRING, "familyName"))
                .addToComments("created through copy-edit fluent API")
                .endProperty()
                .build();

        assertThat(edited).isNotSameAs(person);
        assertThat(edited.getFullyQualifiedName()).isEqualTo("example.people.Person");
        assertThat(edited.getProperties()).extracting(Property::getName)
                .containsExactly("givenName", "familyName");
        assertThat(edited.getMethods()).extracting(Method::getName).containsExactly("displayName");
        assertThat(new TypeDefBuilder(edited)
                .hasMatchingProperty(property -> "givenName".equals(property.getName())))
                .isTrue();
        assertThat(new TypeDefBuilder(edited)
                .buildMatchingProperty(property -> "familyName".equals(property.getName())))
                .satisfies(property -> {
                    assertThat(property.getName()).isEqualTo("familyName");
                    assertThat(property.getTypeRef()).isEqualTo(STRING);
                });
    }

    @Test
    void rendersClassPrimitiveTypeParameterAndWildcardReferences() {
        TypeParamDef key = new TypeParamDefBuilder()
                .withName("K")
                .withBounds(ClassRef.forName("java.lang.Comparable"))
                .build();
        TypeParamDef value = new TypeParamDefBuilder().withName("V").build();
        TypeParamDef arrayItem = new TypeParamDefBuilder().withName("A").build();
        WildcardRef superString = new WildcardRefBuilder()
                .withBoundKind(WildcardRef.BoundKind.SUPER)
                .withBounds(STRING)
                .build();
        WildcardRef extendsNumber = new WildcardRefBuilder()
                .withBoundKind(WildcardRef.BoundKind.EXTENDS)
                .withBounds(NUMBER)
                .build();
        TypeDef holder = new TypeDefBuilder()
                .withKind(Kind.INTERFACE)
                .withPackageName("example.generics")
                .withName("Holder")
                .withParameters(key, value, arrayItem)
                .build();
        PrimitiveRef intArray = INT.withDimensions(2);
        ClassRef holderReference = holder
                .toReference(key.toReference(), superString, intArray)
                .withDimensions(1);
        ClassRef rebuiltReference = new ClassRefBuilder(holderReference)
                .setToArguments(1, extendsNumber)
                .withDimensions(0)
                .build();

        assertThat(key.render()).contains("K").contains("Comparable");
        assertThat(superString.render()).contains("? super").contains("String");
        assertThat(extendsNumber.render()).contains("? extends").contains("Number");
        assertThat(intArray.toString()).isEqualTo("int[][]");
        assertThat(holderReference.getName()).contains("Holder");
        assertThat(holderReference.getArguments()).extracting(Object::toString)
                .containsExactly("K", "? super java.lang.String", "int[][]");
        assertThat(rebuiltReference.getFullyQualifiedName()).isEqualTo("example.generics.Holder");
        assertThat(rebuiltReference.getArguments()).extracting(Object::toString)
                .containsExactly("K", "? extends java.lang.Number", "int[][]");
        assertThat(rebuiltReference.render())
                .contains("Holder")
                .contains("? extends")
                .contains("Number")
                .contains("int[][]");
    }

    @Test
    void rendersComposedExpressionsStatementsAndControlFlow() {
        Property counter = Property.newProperty(INT, "counter");
        PropertyRef counterRef = counter.toReference();
        Expression increment = counterRef.plus(new ValueRef(1));
        Assign assignment = counterRef.assign(increment);
        If conditional = If.gt(counterRef, new ValueRef(0))
                .then(Return.value("positive"))
                .orElse(Return.Null());
        While loop = While.lt(counterRef, new ValueRef(3))
                .body(new PostIncrement(counterRef));
        For forLoop = For.init(new Declare(counter, new ValueRef(0)))
                .lt(counterRef, new ValueRef(2))
                .update(new PostIncrement(counterRef))
                .body(new StringStatement("visited(counter);"));
        MethodCall appendCall = new Construct(ClassRef.forName("java.lang.StringBuilder"))
                .call("append", new ValueRef("value"));
        Ternary ternary = new Ternary(
                new GreaterThan(counterRef, new ValueRef(1)),
                new ValueRef("many"),
                new ValueRef("one"));
        Expression trimCall = Property.newProperty("item").toReference().call("trim");
        Lambda lambda = new Lambda("item", trimCall);
        NewArray array = new NewArray(STRING, 2);
        Try guarded = new Try(
                new Block(Throw.illegalArgument("bad input")),
                List.of(new Try.Catch(
                        Property.newProperty(
                                ClassRef.forName("java.lang.IllegalArgumentException"),
                                "exception"),
                        new Block(Return.value("handled")))),
                Optional.of(new Block(new Empty())));
        Block block = new Block(
                new Declare(counter, new ValueRef(0)),
                assignment,
                conditional,
                loop,
                forLoop,
                new Return(appendCall),
                new Return(ternary),
                new Return(lambda),
                new Return(array),
                guarded);

        String rendered = block.render();

        assertThat(assignment.render()).contains("counter").contains("=").contains("+ 1");
        assertThat(appendCall.render())
                .contains("StringBuilder")
                .contains("append")
                .contains("value");
        assertThat(ternary.render()).contains("?").contains(":");
        assertThat(lambda.render()).contains("item").contains("->").contains("trim");
        assertThat(array.render()).contains("String").contains("[2]");
        assertThat(rendered)
                .contains("if")
                .contains("while")
                .contains("for")
                .contains("throw")
                .contains("IllegalArgumentException")
                .contains("catch")
                .contains("finally")
                .contains("handled");
        assertThat(block.getReferences()).extracting(ClassRef::getFullyQualifiedName)
                .contains(
                        "java.lang.StringBuilder",
                        "java.lang.IllegalArgumentException",
                        "java.lang.String");
    }

    @Test
    void rendersAdditionalStructuredStatements() {
        Property state = Property.newProperty(STRING, "state");
        Property name = Property.newProperty(STRING, "name");
        Property names = Property.newProperty(ClassRef.forName("java.util.List"), "names");
        Block startBlock = new Block(new Return(new ValueRef("ready")));
        Block stopBlock = new Block(new Break());
        Block defaultBlock = new Block(new Return(new ValueRef("unknown")));
        ValueRef startCase = new ValueRef("start");
        ValueRef stopCase = new ValueRef("stop");
        Map<ValueRef, Block> cases = new LinkedHashMap<>();
        cases.put(startCase, startBlock);
        cases.put(stopCase, stopBlock);
        ValueRef keepRunning = new ValueRef(false);

        Switch switchStatement = new Switch(state.toReference(), cases, Optional.of(defaultBlock));
        Foreach foreach = new Foreach(name, names, new Continue());
        Synchronized synchronizedStatement = Synchronized.on(Property.newProperty("lock").toReference())
                .body(foreach, switchStatement);
        Do doStatement = new Do(keepRunning, synchronizedStatement);

        String switchRender = switchStatement.render();
        String foreachRender = foreach.render();
        String synchronizedRender = synchronizedStatement.render();
        String doRender = doStatement.render();

        assertThat(switchStatement.getExpression().render()).isEqualTo("state");
        assertThat(switchStatement.getCases()).containsEntry(startCase, startBlock);
        assertThat(switchStatement.getDefaultCase()).hasValue(defaultBlock);
        assertThat(switchRender)
                .contains("switch")
                .contains("case")
                .contains("default")
                .contains("break;")
                .contains("ready")
                .contains("unknown");
        assertThat(foreach.getDeclare().getProperties()).containsExactly(name);
        assertThat(foreach.getExpression()).isEqualTo(names);
        assertThat(foreachRender)
                .contains("for")
                .contains("String")
                .contains("name")
                .contains("names")
                .contains("continue;");
        assertThat(synchronizedStatement.getLockExpression().render()).isEqualTo("lock");
        assertThat(synchronizedRender)
                .contains("synchronized")
                .contains("lock")
                .contains("switch")
                .contains("continue;");
        assertThat(doStatement.getCondition()).isEqualTo(keepRunning);
        assertThat(doRender)
                .contains("do")
                .contains("synchronized")
                .contains("while")
                .contains("false");
        assertThat(doStatement.getReferences()).extracting(ClassRef::getFullyQualifiedName)
                .contains("java.lang.String", "java.util.List");
    }

    @Test
    void propertiesMethodsModifiersValuesAndReferencesExposeConvenienceBehavior() {
        AttributeKey<String> ownerAttribute = new AttributeKey<>("owner", String.class);
        Property name = new PropertyBuilder()
                .withModifiers(privateFinalModifiers())
                .withTypeRef(STRING)
                .withName("name")
                .withInitialValue(new ValueRef("unknown"))
                .addToAttributes(ownerAttribute, "model-test")
                .build();
        Method withVarArg = new MethodBuilder()
                .withName("format")
                .withReturnType(STRING)
                .withArguments(Property.newProperty(STRING.withDimensions(1), "parts"))
                .withVarArgPreferred()
                .withExceptions(ClassRef.forName("java.io.IOException"))
                .withBlock(new Block(new Return(new MethodCall(
                        "join",
                        STRING,
                        new ValueRef(","),
                        name.toReference()))))
                .build();
        Property erasedName = name.withErasure();
        Property initializedName = name.withoutInitialValue().withInitialValue("created");
        Modifiers modifiersFromInt = Modifiers.from(publicModifiers().toInt());

        assertThat(name.hasAttribute(ownerAttribute)).isTrue();
        assertThat(name.getAttribute(ownerAttribute)).isEqualTo("model-test");
        assertThat(name.getInitialValue())
                .hasValueSatisfying(value -> assertThat(value.render()).contains("unknown"));
        assertThat(erasedName.getErasure()).isEqualTo(name.getErasure());
        assertThat(initializedName.getInitialValue())
                .hasValueSatisfying(value -> assertThat(value.render()).contains("created"));
        assertThat(name.toReference().render()).isEqualTo("name");
        assertThat(withVarArg.isVarArgPreferred()).isTrue();
        assertThat(withVarArg.getSignature()).contains("format").contains("String");
        assertThat(withVarArg.getReferences()).extracting(ClassRef::getFullyQualifiedName)
                .contains("java.lang.String", "java.io.IOException");
        assertThat(modifiersFromInt).isEqualTo(publicModifiers());
        assertThat(modifiersFromInt.isPublic()).isTrue();
        assertThat(Modifiers.create()).isEqualTo(new Modifiers());
        assertThat(ValueRef.from("a", "b").render()).contains("a").contains("b");
        assertThat(ValueRef.NULL.render()).isEqualTo("null");
    }

    private static Modifiers publicModifiers() {
        return new Modifiers(false, false, true, false, false, false, false, false, false);
    }

    private static Modifiers privateFinalModifiers() {
        return new Modifiers(true, false, false, false, true, false, false, false, false);
    }
}
