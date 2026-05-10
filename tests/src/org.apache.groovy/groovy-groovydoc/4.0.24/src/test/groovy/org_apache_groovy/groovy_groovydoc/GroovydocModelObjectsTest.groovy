/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_groovydoc

import org.codehaus.groovy.groovydoc.GroovyTag
import org.codehaus.groovy.tools.groovydoc.LinkArgument
import org.codehaus.groovy.tools.groovydoc.SimpleGroovyClassDoc
import org.codehaus.groovy.tools.groovydoc.SimpleGroovyDoc
import org.codehaus.groovy.tools.groovydoc.SimpleGroovyFieldDoc
import org.codehaus.groovy.tools.groovydoc.SimpleGroovyMethodDoc
import org.codehaus.groovy.tools.groovydoc.SimpleGroovyParameter
import org.codehaus.groovy.tools.groovydoc.SimpleGroovyTag
import org.codehaus.groovy.tools.groovydoc.SimpleGroovyType
import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThat

public class GroovydocModelObjectsTest {
    @Test
    void simpleDocParsesTagsAndReportsDefinitionKinds() {
        SimpleGroovyDoc doc = new SimpleGroovyDoc('DocumentedThing')
        doc.setRawCommentText('''
            /**
             * First sentence. Second sentence.
             *
             * @param name supplied name
             * @return calculated result
             * @deprecated use the replacement type
             */
        '''.stripIndent())

        assertThat(doc.name()).isEqualTo('DocumentedThing')
        assertThat(SimpleGroovyDoc.calculateFirstSentence('First sentence. Second sentence.').trim()).isEqualTo('First sentence.')
        assertThat(doc.isClass()).isTrue()
        assertThat(doc.getTypeDescription()).isEqualTo('Class')
        assertThat(doc.getTypeSourceDescription()).isEqualTo('class')
        assertThat(doc.isDeprecated()).isTrue()

        List<GroovyTag> tags = doc.tags() as List<GroovyTag>
        assertThat(tags*.name()).contains('param', 'return', 'deprecated')
        assertThat(tags.find { it.name() == 'param' }.param()).isEqualTo('name')
        assertThat(tags.find { it.name() == 'param' }.text().trim()).isEqualTo('supplied name')
        assertThat(tags.find { it.name() == 'return' }.text().trim()).isEqualTo('calculated result')

        doc.setTokenType(SimpleGroovyDoc.INTERFACE_DEF)
        assertThat(doc.tokenType()).isEqualTo(SimpleGroovyDoc.INTERFACE_DEF)
        assertThat(doc.isInterface()).isTrue()
        assertThat(doc.getTypeDescription()).isEqualTo('Interface')
        assertThat(doc.getTypeSourceDescription()).isEqualTo('interface')

        doc.setTokenType(SimpleGroovyDoc.ENUM_DEF)
        assertThat(doc.isEnum()).isTrue()
        assertThat(doc.getTypeDescription()).isEqualTo('Enum')
        assertThat(doc.getTypeSourceDescription()).isEqualTo('enum')
    }

    @Test
    void simpleTypesParametersAndMemberDocsExposeConfiguredModelData() {
        SimpleGroovyType mapType = new SimpleGroovyType('java.util.Map')
        SimpleGroovyType defaultPackageType = new SimpleGroovyType('DefaultPackage.LocalThing')
        SimpleGroovyParameter parameter = new SimpleGroovyParameter('values')
        parameter.setTypeName('List')
        parameter.setDefaultValue('[]')
        parameter.setVararg(true)

        assertThat(mapType.typeName()).isEqualTo('java.util.Map')
        assertThat(mapType.qualifiedTypeName()).isEqualTo('java.util.Map')
        assertThat(mapType.simpleTypeName()).isEqualTo('Map')
        assertThat(defaultPackageType.qualifiedTypeName()).isEqualTo('LocalThing')
        assertThat(defaultPackageType.simpleTypeName()).isEqualTo('LocalThing')
        assertThat(mapType.isPrimitive()).isFalse()

        assertThat(parameter.name()).isEqualTo('values')
        assertThat(parameter.typeName()).isEqualTo('List')
        assertThat(parameter.defaultValue()).isEqualTo('[]')
        assertThat(parameter.vararg()).isTrue()
        assertThat(parameter.isTypeAvailable()).isFalse()

        parameter.setType(mapType)
        assertThat(parameter.isTypeAvailable()).isTrue()
        assertThat(parameter.type()).isSameAs(mapType)
        assertThat(parameter.typeName()).isEqualTo('Map')

        SimpleGroovyTag tag = new SimpleGroovyTag('throws', 'IOException', 'when input cannot be read')
        assertThat(tag.name()).isEqualTo('throws')
        assertThat(tag.param()).isEqualTo('IOException')
        assertThat(tag.text()).isEqualTo('when input cannot be read')
    }

    @Test
    void simpleClassDocStoresFieldsMethodsPropertiesAndExternalLinks() {
        LinkArgument externalLink = new LinkArgument()
        externalLink.setPackages('com.acme.')
        externalLink.setHref('https://docs.example.invalid/api')
        SimpleGroovyClassDoc classDoc = new SimpleGroovyClassDoc(
                ['java.util.List'] as List<String>,
                ['Alias': 'com.acme.Alias'] as Map<String, String>,
                'com/example/Documented',
                [externalLink])
        classDoc.setFullPathName('com/example/Documented')
        classDoc.setGroovy(true)
        classDoc.setNameWithTypeArgs('Documented<T>')

        SimpleGroovyFieldDoc fieldDoc = new SimpleGroovyFieldDoc('count', classDoc)
        fieldDoc.setType(new SimpleGroovyType('int'))
        fieldDoc.setConstantValueExpression('42')
        SimpleGroovyFieldDoc propertyDoc = new SimpleGroovyFieldDoc('title', classDoc)
        propertyDoc.setType(new SimpleGroovyType('java.lang.String'))
        SimpleGroovyMethodDoc methodDoc = new SimpleGroovyMethodDoc('compute', classDoc)
        methodDoc.setReturnType(new SimpleGroovyType('java.math.BigDecimal'))
        methodDoc.setTypeParameters('<T>')
        SimpleGroovyParameter parameter = new SimpleGroovyParameter('input')
        parameter.setType(new SimpleGroovyType('java.lang.String'))
        methodDoc.add(parameter)

        classDoc.add(fieldDoc)
        classDoc.addProperty(propertyDoc)
        classDoc.add(methodDoc)

        assertThat(classDoc.isGroovy()).isTrue()
        assertThat(classDoc.qualifiedTypeName()).isEqualTo('com.example.Documented')
        assertThat(classDoc.simpleTypeName()).isEqualTo('Documented')
        assertThat(classDoc.getRelativeRootPath()).isEqualTo('../../')
        assertThat(classDoc.getNameWithTypeArgs()).isEqualTo('Documented<T>')
        assertThat(classDoc.fields()).containsExactly(fieldDoc)
        assertThat(classDoc.properties()).containsExactly(propertyDoc)
        assertThat(classDoc.methods()).containsExactly(methodDoc)

        assertThat(fieldDoc.type().typeName()).isEqualTo('int')
        assertThat(fieldDoc.constantValueExpression()).isEqualTo('42')
        assertThat(propertyDoc.type().simpleTypeName()).isEqualTo('String')
        assertThat(methodDoc.returnType().simpleTypeName()).isEqualTo('BigDecimal')
        assertThat(methodDoc.typeParameters()).isEqualTo('<T>')
        assertThat(methodDoc.parameters()*.name()).containsExactly('input')
        assertThat(methodDoc.parameters()*.typeName()).containsExactly('String')

        assertThat(classDoc.getDocUrl('com.acme.Service'))
                .contains("href='https://docs.example.invalid/api/com/acme/Service.html'", '>Service</a>')
    }
}
