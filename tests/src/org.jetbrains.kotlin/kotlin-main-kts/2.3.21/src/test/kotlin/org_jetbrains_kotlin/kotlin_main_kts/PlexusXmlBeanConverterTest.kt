/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_main_kts

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.com.google.inject.AbstractModule
import org.jetbrains.kotlin.com.google.inject.Guice
import org.jetbrains.kotlin.com.google.inject.TypeLiteral
import org.jetbrains.kotlin.org.eclipse.sisu.plexus.PlexusBeanConverter
import org.jetbrains.kotlin.org.eclipse.sisu.plexus.PlexusXmlBeanConverter
import org.junit.jupiter.api.Test

public class PlexusXmlBeanConverterTest {
    private val converter: PlexusBeanConverter = Guice.createInjector(
        object : AbstractModule() {
            override fun configure(): Unit {
                bind(PlexusBeanConverter::class.java).to(PlexusXmlBeanConverter::class.java)
            }
        },
    ).getInstance(PlexusBeanConverter::class.java)

    @Test
    public fun convertsXmlCollectionsToArrays(): Unit {
        val values: Array<String> = converter.convert(
            TypeLiteral.get(Array<String>::class.java),
            """
            <values>
              <value>alpha</value>
              <value>beta</value>
            </values>
            """.trimIndent(),
        )

        assertThat(values).containsExactly("alpha", "beta")
    }

    @Test
    public fun convertsTextUsingPublicStringConstructor(): Unit {
        val value: PlexusXmlBeanConverterTextValue = converter.convert(
            TypeLiteral.get(PlexusXmlBeanConverterTextValue::class.java),
            "constructed from text",
        )

        assertThat(value.text).isEqualTo("constructed from text")
    }

    @Test
    public fun loadsXmlImplementationWithThreadContextClassLoader(): Unit {
        val previousClassLoader: ClassLoader? = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = PlexusXmlBeanConverterImplementedBean::class.java.classLoader
        try {
            val bean: PlexusXmlBeanConverterBaseBean = converter.convert(
                TypeLiteral.get(PlexusXmlBeanConverterBaseBean::class.java),
                implementationXml(PlexusXmlBeanConverterImplementedBean::class.java.name, "tccl"),
            )

            assertThat(bean).isInstanceOf(PlexusXmlBeanConverterImplementedBean::class.java)
            assertThat(bean.name).isEqualTo("tccl")
        } finally {
            Thread.currentThread().contextClassLoader = previousClassLoader
        }
    }

    @Test
    public fun fallsBackToRoleClassLoaderWhenThreadContextClassLoaderCannotLoadImplementation(): Unit {
        withRejectingThreadContextClassLoader {
            val bean: PlexusXmlBeanConverterBaseBean = converter.convert(
                TypeLiteral.get(PlexusXmlBeanConverterBaseBean::class.java),
                implementationXml(PlexusXmlBeanConverterImplementedBean::class.java.name, "role loader"),
            )

            assertThat(bean).isInstanceOf(PlexusXmlBeanConverterImplementedBean::class.java)
            assertThat(bean.name).isEqualTo("role loader")
        }
    }

    @Test
    public fun fallsBackToClassForNameWhenRoleClassUsesBootstrapClassLoader(): Unit {
        withRejectingThreadContextClassLoader {
            val number: Number = converter.convert(
                TypeLiteral.get(Number::class.java),
                """
                <number implementation="java.lang.Integer">42</number>
                """.trimIndent(),
            )

            assertThat(number).isEqualTo(42)
        }
    }

    private fun implementationXml(implementation: String, name: String): String =
        """
        <bean implementation="$implementation">
          <name>$name</name>
        </bean>
        """.trimIndent()

    private fun withRejectingThreadContextClassLoader(block: () -> Unit): Unit {
        val thread: Thread = Thread.currentThread()
        val previousClassLoader: ClassLoader? = thread.contextClassLoader
        thread.contextClassLoader = RejectingClassLoader()
        try {
            block()
        } finally {
            thread.contextClassLoader = previousClassLoader
        }
    }

    public class RejectingClassLoader : ClassLoader(null) {
        override fun loadClass(name: String): Class<*> {
            throw ClassNotFoundException(name)
        }
    }
}

public class PlexusXmlBeanConverterTextValue(public val text: String)

public open class PlexusXmlBeanConverterBaseBean {
    public var name: String = ""
}

public class PlexusXmlBeanConverterImplementedBean : PlexusXmlBeanConverterBaseBean()
