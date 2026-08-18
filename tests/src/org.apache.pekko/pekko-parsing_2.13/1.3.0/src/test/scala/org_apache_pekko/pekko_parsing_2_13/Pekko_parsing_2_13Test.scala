/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_parsing_2_13

import org.apache.pekko.http.ccompat.{pre213, since213}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.annotation.StaticAnnotation
import scala.jdk.CollectionConverters._

class Pekko_parsing_2_13Test {
  @Test
  def annotationClassesAreAvailableAsStaticAnnotations(): Unit = {
    val annotationClasses: List[Class[_ <: StaticAnnotation]] = List(
      loadStaticAnnotation("org.apache.pekko.http.ccompat.pre213"),
      loadStaticAnnotation("org.apache.pekko.http.ccompat.since213"))

    assertThat(annotationClasses.map(_.getName).asJava)
      .containsExactly(
        "org.apache.pekko.http.ccompat.pre213",
        "org.apache.pekko.http.ccompat.since213")
    annotationClasses.foreach { annotationClass =>
      assertThat(classOf[StaticAnnotation].isAssignableFrom(annotationClass)).isTrue
      assertThat(annotationClass.getDeclaredConstructor()).isNotNull
    }
  }

  @Test
  def compatibilityMacroClassesLoadWithScalaReflectOnTheClasspath(): Unit = {
    val macroClasses: List[Class[_]] = List(
      loadClass("org.apache.pekko.http.ccompat.pre213macro"),
      loadClass("org.apache.pekko.http.ccompat.pre213macro$"),
      loadClass("org.apache.pekko.http.ccompat.since213macro"),
      loadClass("org.apache.pekko.http.ccompat.since213macro$"))

    assertThat(macroClasses.map(_.getName).asJava)
      .containsExactly(
        "org.apache.pekko.http.ccompat.pre213macro",
        "org.apache.pekko.http.ccompat.pre213macro$",
        "org.apache.pekko.http.ccompat.since213macro",
        "org.apache.pekko.http.ccompat.since213macro$")
    assertThat(macroClasses.forall(_.getClassLoader != null)).isTrue
  }

  @Test
  def logHelperMacroSupportClassesLoadWithProvidedPekkoActorAndScalaReflectDependencies(): Unit = {
    val logHelperClass: Class[_] = loadClass("org.apache.pekko.macros.LogHelper")
    val logHelperMacroClass: Class[_] = loadClass("org.apache.pekko.macros.LogHelperMacro")
    val logHelperMacroModuleClass: Class[_] = loadClass("org.apache.pekko.macros.LogHelperMacro$")
    val loggingAdapterClass: Class[_] = loadClass("org.apache.pekko.event.LoggingAdapter")
    val scalaMacroContextClass: Class[_] = loadClass("scala.reflect.macros.blackbox.Context")

    assertThat(logHelperClass.isInterface).isTrue
    assertThat(logHelperMacroClass.isInterface).isTrue
    assertThat(logHelperMacroClass.isAssignableFrom(logHelperClass)).isTrue
    assertThat(logHelperMacroModuleClass.getName).endsWith("LogHelperMacro$")
    assertThat(loggingAdapterClass.getName).isEqualTo("org.apache.pekko.event.LoggingAdapter")
    assertThat(scalaMacroContextClass.getName).isEqualTo("scala.reflect.macros.blackbox.Context")
  }

  @Test
  def annotationInstancesCanBeUsedToDispatchCrossVersionBehaviorAtRuntime(): Unit = {
    val annotations: List[(StaticAnnotation, String)] = List(
      newStaticAnnotation("org.apache.pekko.http.ccompat.pre213") -> "legacy",
      newStaticAnnotation("org.apache.pekko.http.ccompat.since213") -> "current")

    val renderedTokens: List[String] = annotations.map { case (annotation, token) =>
      renderTokenFor(annotation, token)
    }

    assertThat(renderedTokens.asJava).containsExactly("pre213:legacy", "since213:current")
  }

  @Test
  def annotationTypesCanBeUsedToSelectCrossVersionImplementationsAtRuntime(): Unit = {
    val pre213Class: Class[_ <: StaticAnnotation] = loadStaticAnnotation("org.apache.pekko.http.ccompat.pre213")
    val since213Class: Class[_ <: StaticAnnotation] = loadStaticAnnotation("org.apache.pekko.http.ccompat.since213")
    val selectors: Map[Class[_ <: StaticAnnotation], String => java.util.List[String]] = Map(
      pre213Class -> commaSeparatedTokens,
      since213Class -> pipeSeparatedTokens)

    assertThat(selectors(pre213Class).apply("alpha, beta,,gamma"))
      .containsExactly("alpha", "beta", "gamma")
    assertThat(selectors(since213Class).apply("delta| epsilon || zeta"))
      .containsExactly("delta", "epsilon", "zeta")
  }

  @Test
  def loadedMacroSupportClassesCanBeGroupedByTheirPekkoPackage(): Unit = {
    val classes: List[Class[_]] = List(
      loadClass("org.apache.pekko.http.ccompat.pre213"),
      loadClass("org.apache.pekko.http.ccompat.since213"),
      loadClass("org.apache.pekko.http.ccompat.pre213macro"),
      loadClass("org.apache.pekko.http.ccompat.since213macro"),
      loadClass("org.apache.pekko.macros.LogHelper"),
      loadClass("org.apache.pekko.macros.LogHelperMacro"))

    val groupedNames: Map[String, List[String]] = classes
      .groupBy(_.getPackage.getName)
      .view
      .mapValues(_.map(_.getSimpleName).sorted)
      .toMap

    assertThat(groupedNames("org.apache.pekko.http.ccompat").asJava)
      .containsExactly("pre213", "pre213macro", "since213", "since213macro")
    assertThat(groupedNames("org.apache.pekko.macros").asJava)
      .containsExactly("LogHelper", "LogHelperMacro")
  }

  @Test
  def crossVersionMacroAnnotationsSelectScala213ImplementationAtCompileTime(): Unit = {
    assertThat(CrossVersionParsingRules.separatorName).isEqualTo("pipe")
    assertThat(CrossVersionParsingRules.normalizedSegments("alpha | beta || gamma").asJava)
      .containsExactly("alpha", "beta", "gamma")
  }

  private def renderTokenFor(annotation: StaticAnnotation, token: String): String =
    annotation.getClass.getName match {
      case "org.apache.pekko.http.ccompat.pre213" => s"pre213:$token"
      case "org.apache.pekko.http.ccompat.since213" => s"since213:$token"
    }

  private def newStaticAnnotation(name: String): StaticAnnotation =
    loadStaticAnnotation(name).getDeclaredConstructor().newInstance()

  private def loadClass(name: String): Class[_] =
    Class.forName(name, true, Thread.currentThread().getContextClassLoader)

  private def loadStaticAnnotation(name: String): Class[_ <: StaticAnnotation] = {
    val loadedClass: Class[_] = loadClass(name)
    assertThat(classOf[StaticAnnotation].isAssignableFrom(loadedClass)).isTrue
    loadedClass.asSubclass(classOf[StaticAnnotation])
  }

  private def commaSeparatedTokens(value: String): java.util.List[String] =
    tokens(value, ",")

  private def pipeSeparatedTokens(value: String): java.util.List[String] =
    tokens(value, "\\|")

  private def tokens(value: String, separator: String): java.util.List[String] =
    splitAndTrim(value, separator).asJava

  private def splitAndTrim(value: String, separator: String): List[String] =
    value
      .split(separator)
      .iterator
      .map(_.trim)
      .filter(_.nonEmpty)
      .toList

  private object CrossVersionParsingRules {
    @pre213
    def separatorName: String = "comma"

    @since213
    def separatorName: String = "pipe"

    @pre213
    def normalizedSegments(value: String): List[String] =
      splitAndTrim(value, ",")

    @since213
    def normalizedSegments(value: String): List[String] =
      splitAndTrim(value, "\\|")
  }
}
