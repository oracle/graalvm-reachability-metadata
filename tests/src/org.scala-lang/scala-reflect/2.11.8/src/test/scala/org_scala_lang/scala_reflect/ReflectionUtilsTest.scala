/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang.scala_reflect

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.reflect.internal.util.AbstractFileClassLoader
import scala.reflect.io.VirtualDirectory
import scala.reflect.runtime.ReflectionUtils

class ReflectionUtilsTest {
  private val classLoader: ClassLoader = getClass.getClassLoader

  @Test
  def loadsStaticSingletonModuleByName(): Unit = {
    val singleton: AnyRef = ReflectionUtils.staticSingletonInstance(
      classLoader,
      "org_scala_lang.scala_reflect.ReflectionUtilsStaticSingletonFixture"
    )

    assertThat(singleton).isSameAs(ReflectionUtilsStaticSingletonFixture)
    assertThat(ReflectionUtilsStaticSingletonFixture.message).isEqualTo("static-singleton")
  }

  @Test
  def invokesInnerSingletonAccessorOnOuterInstance(): Unit = {
    val outer: ReflectionUtilsOuterFixture = new ReflectionUtilsOuterFixture("outer-id")

    val singleton: AnyRef = ReflectionUtils.innerSingletonInstance(outer, "NestedSingleton")

    assertThat(singleton).isSameAs(outer.NestedSingleton)
    assertThat(outer.NestedSingleton.description).isEqualTo("inner-singleton:outer-id")
  }

  @Test
  def describesAbstractFileClassLoaderClasspath(): Unit = {
    val root: VirtualDirectory = new VirtualDirectory("reflection-utils-root", None)
    val loader: AbstractFileClassLoader = new AbstractFileClassLoader(root, classLoader)

    val description: String = ReflectionUtils.show(loader)

    assertThat(description).contains("AbstractFileClassLoader")
    assertThat(description).contains("reflection-utils-root")
  }
}

object ReflectionUtilsStaticSingletonFixture {
  def message: String = "static-singleton"
}

class ReflectionUtilsOuterFixture(private val id: String) {
  object NestedSingleton {
    def description: String = s"inner-singleton:$id"
  }
}
