/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework_anorm.anorm_3

import java.lang.module.Configuration
import java.lang.module.FindException
import java.lang.module.ModuleFinder
import java.lang.reflect.Type
import java.nio.file.Path
import java.nio.file.Paths
import java.util.OptionalDouble
import java.util.OptionalInt
import java.util.OptionalLong
import java.util.Set

import com.google.common.reflect.TypeToken
import org.graalvm.internal.tck.NativeImageSupport
import org.joda.convert.StringConvert
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

import scala.annotation.static
import scala.jdk.CollectionConverters.*

class StringConvertTest {
  @Test
  def constructorRegistersGuavaAndJavaOptionalConverters(): Unit = {
    val convert: StringConvert = new StringConvert()

    val optionalInt: OptionalInt = convert.convertFromString(classOf[OptionalInt], "17")
    val optionalLong: OptionalLong = convert.convertFromString(classOf[OptionalLong], "19")
    val optionalDouble: OptionalDouble = convert.convertFromString(classOf[OptionalDouble], "23.5")
    val token: TypeToken[?] = convert.convertFromString(
      classOf[TypeToken[?]],
      "java.util.List<java.lang.String>"
    )

    assertEquals(17, optionalInt.getAsInt)
    assertEquals(19L, optionalLong.getAsLong)
    assertEquals(23.5d, optionalDouble.getAsDouble, 0.0d)
    assertEquals("17", convert.convertToString(optionalInt))
    assertEquals("19", convert.convertToString(optionalLong))
    assertEquals("23.5", convert.convertToString(optionalDouble))
    assertEquals("java.util.List<java.lang.String>", token.toString)
  }

  @Test
  def typeParsingUsesTheContextClassLoaderWhenItIsAvailable(): Unit = {
    val convert: StringConvert = new StringConvert()

    val parsed: Type = convert.convertFromString(
      classOf[Type],
      "java.util.Map<java.lang.String, java.lang.Integer>"
    )

    assertEquals("java.util.Map<java.lang.String, java.lang.Integer>", parsed.getTypeName)
  }

  @Test
  def typeParsingUsesClassForNameWhenThereIsNoContextClassLoader(): Unit = {
    val thread: Thread = Thread.currentThread()
    val originalLoader: ClassLoader = thread.getContextClassLoader
    thread.setContextClassLoader(null)
    try {
      val convert: StringConvert = new StringConvert()
      val parsed: Type = convert.convertFromString(classOf[Type], "java.lang.String")

      assertEquals(classOf[String], parsed)
    } finally {
      thread.setContextClassLoader(originalLoader)
    }
  }

  @Test
  def registerMethodsAcceptsStaticStringFactoryMethods(): Unit = {
    val convert: StringConvert = new StringConvert(false)
    convert.registerMethods(classOf[StringMethodValue], "asText", "parse")

    val parsed: StringMethodValue = convert.convertFromString(classOf[StringMethodValue], "from-string-method")

    assertEquals("from-string-method", parsed.asText)
    assertEquals("from-string-method", convert.convertToString(parsed))
  }

  @Test
  def registerMethodsFallsBackToStaticCharSequenceFactoryMethods(): Unit = {
    val convert: StringConvert = new StringConvert(false)
    convert.registerMethods(classOf[CharSequenceMethodValue], "asText", "parse")

    val parsed: CharSequenceMethodValue = convert.convertFromString(
      classOf[CharSequenceMethodValue],
      "from-char-sequence-method"
    )

    assertEquals("from-char-sequence-method", parsed.asText)
    assertEquals("from-char-sequence-method", convert.convertToString(parsed))
  }

  @Test
  def registerMethodConstructorAcceptsStringConstructors(): Unit = {
    val convert: StringConvert = new StringConvert(false)
    convert.registerMethodConstructor(classOf[StringConstructorValue], "asText")

    val parsed: StringConstructorValue = convert.convertFromString(
      classOf[StringConstructorValue],
      "from-string-constructor"
    )

    assertEquals("from-string-constructor", parsed.asText)
    assertEquals("from-string-constructor", convert.convertToString(parsed))
  }

  @Test
  def registerMethodConstructorFallsBackToCharSequenceConstructors(): Unit = {
    val convert: StringConvert = new StringConvert(false)
    convert.registerMethodConstructor(classOf[CharSequenceConstructorValue], "asText")

    val parsed: CharSequenceConstructorValue = convert.convertFromString(
      classOf[CharSequenceConstructorValue],
      "from-char-sequence-constructor"
    )

    assertEquals("from-char-sequence-constructor", parsed.asText)
    assertEquals("from-char-sequence-constructor", convert.convertToString(parsed))
  }

  @Test
  def constructorAddsAReadEdgeToGuavaWhenLoadedAsNamedModules(): Unit = {
    try {
      assertEquals("java.lang.String", convertGuavaTypeTokenInNamedModuleLayer())
    } catch {
      case error: Error if NativeImageSupport.isUnsupportedFeatureError(error) =>
      case exception: FindException if isNativeImageCodeSourceFailure(exception) =>
        // Native Image code sources point at the executable, not the original modular JARs.
    }
  }

  private def convertGuavaTypeTokenInNamedModuleLayer(): String = {
    val jodaConvertJar: Path = jarPath(classOf[StringConvert])
    val classPathTypeTokenClass: Class[?] = Class.forName("com.google.common.reflect.TypeToken")
    val guavaJar: Path = jarPath(classPathTypeTokenClass)
    val failureAccessClass: Class[?] = Class.forName(
      "com.google.common.util.concurrent.internal.InternalFutureFailureAccess"
    )
    val failureAccessJar: Path = jarPath(failureAccessClass)
    val finder: ModuleFinder = ModuleFinder.of(jodaConvertJar, guavaJar, failureAccessJar)
    val jodaConvertModuleName: String = moduleName(finder, "org.joda.convert")
    val guavaModuleName: String = moduleName(finder, "com.google.common.reflect")
    val configuration: Configuration = ModuleLayer.boot().configuration().resolve(
      finder,
      ModuleFinder.of(),
      Set.of(jodaConvertModuleName, guavaModuleName)
    )
    val layer: ModuleLayer = ModuleLayer.boot().defineModulesWithOneLoader(
      configuration,
      ClassLoader.getPlatformClassLoader
    )
    val loader: ClassLoader = layer.findLoader(jodaConvertModuleName)
    val thread: Thread = Thread.currentThread()
    val originalLoader: ClassLoader = thread.getContextClassLoader
    thread.setContextClassLoader(loader)
    try {
      val stringConvertType: Class[?] = Class.forName("org.joda.convert.StringConvert", true, loader)
      val convert: Object = stringConvertType.getConstructor().newInstance().asInstanceOf[Object]
      val typeTokenClass: Class[?] = Class.forName("com.google.common.reflect.TypeToken", true, loader)
      val token: Object = typeTokenClass.getMethod("of", classOf[Type]).invoke(null, classOf[String])

      stringConvertType
        .getMethod("convertToString", classOf[Class[?]], classOf[Object])
        .invoke(convert, typeTokenClass, token)
        .asInstanceOf[String]
    } finally {
      thread.setContextClassLoader(originalLoader)
    }
  }

  private def jarPath(`type`: Class[?]): Path = {
    Paths.get(`type`.getProtectionDomain.getCodeSource.getLocation.toURI)
  }

  private def isNativeImageCodeSourceFailure(exception: FindException): Boolean = {
    val message: String = Option(exception.getMessage).getOrElse("")
    message.contains("Unable to derive module descriptor") || message.contains("Module format not recognized")
  }

  private def moduleName(finder: ModuleFinder, packageName: String): String = {
    finder
      .findAll()
      .asScala
      .find(reference => reference.descriptor().packages().contains(packageName))
      .getOrElse(throw new IllegalStateException(s"No module contains package $packageName"))
      .descriptor()
      .name()
  }
}

final class StringMethodValue(private val value: String) {
  def asText: String = value
}

object StringMethodValue {
  @static def parse(value: String): StringMethodValue = new StringMethodValue(value)
}

final class CharSequenceMethodValue(private val value: CharSequence) {
  def asText: String = value.toString
}

object CharSequenceMethodValue {
  @static def parse(value: CharSequence): CharSequenceMethodValue = new CharSequenceMethodValue(value)
}

final class StringConstructorValue(private val value: String) {
  def asText: String = value
}

final class CharSequenceConstructorValue(private val value: CharSequence) {
  def asText: String = value.toString
}
