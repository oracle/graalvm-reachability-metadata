/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_portable_scala.portable_scala_reflect_2_13

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable
import org.portablescala.reflect.Reflect
import org.portablescala.reflect.annotation.EnableReflectiveInstantiation

final class Portable_scala_reflect_2_13Test {
  import PortableScalaReflectFixtures.Accessors
  import PortableScalaReflectFixtures.ConstructorThrowsMessage
  import PortableScalaReflectFixtures.ValueClass

  @Test
  def lookupInstantiatableClassFindsAnnotatedConcreteClasses(): Unit = {
    for (name <- Seq(Names.DirectClass, Names.DirectClassWithoutNoArgConstructor,
        Names.IndirectClass, Names.IndirectClassWithoutNoArgConstructor)) {
      val instantiatableClass = Reflect.lookupInstantiatableClass(name)

      assertTrue(instantiatableClass.isDefined, name)
      assertEquals(name, instantiatableClass.get.runtimeClass.getName)
    }
  }

  @Test
  def lookupInstantiatableClassRejectsUnsupportedOrUnannotatedTypes(): Unit = {
    val unsupportedNames = Seq(
      Names.DirectModule,
      Names.DirectTrait,
      Names.DirectAbstractClass,
      Names.DirectClassWithoutPublicConstructor,
      Names.IndirectModule,
      Names.IndirectTrait,
      Names.IndirectAbstractClass,
      Names.IndirectClassWithoutPublicConstructor,
      Names.UnannotatedClass,
      Names.UnannotatedModule,
      Names.UnannotatedTrait,
      Names.MissingClass)

    for (name <- unsupportedNames) {
      assertTrue(Reflect.lookupInstantiatableClass(name).isEmpty, name)
    }
  }

  @Test
  def lookupLoadableModuleClassFindsAnnotatedSingletonObjects(): Unit = {
    for (name <- Seq(Names.DirectModule, Names.IndirectModule)) {
      val loadableModuleClass = Reflect.lookupLoadableModuleClass(name)

      assertTrue(loadableModuleClass.isDefined, name)
      assertEquals(name, loadableModuleClass.get.runtimeClass.getName)
    }
  }

  @Test
  def lookupLoadableModuleClassRejectsClassesTraitsAndUnannotatedModules(): Unit = {
    val unsupportedNames = Seq(
      Names.DirectClass,
      Names.DirectClassWithoutNoArgConstructor,
      Names.DirectTrait,
      Names.DirectAbstractClass,
      Names.DirectClassWithoutPublicConstructor,
      Names.IndirectClass,
      Names.IndirectClassWithoutNoArgConstructor,
      Names.IndirectTrait,
      Names.IndirectAbstractClass,
      Names.IndirectClassWithoutPublicConstructor,
      Names.UnannotatedClass,
      Names.UnannotatedModule,
      Names.UnannotatedTrait,
      Names.MissingClass)

    for (name <- unsupportedNames) {
      assertTrue(Reflect.lookupLoadableModuleClass(name).isEmpty, name)
    }
  }

  @Test
  def classWrappersExposePublicConstructorsAndCreateInstances(): Unit = {
    for (name <- Seq(Names.DirectClass, Names.IndirectClass)) {
      val instantiatableClass = Reflect.lookupInstantiatableClass(name).get
      val instance = instantiatableClass.newInstance().asInstanceOf[Accessors]

      assertEquals(-1, instance.x, name)
      assertEquals(name.stripPrefix(Names.Prefix), instance.y, name)
    }
  }

  @Test
  def noArgumentInstantiationRequiresAPublicZeroArgumentConstructor(): Unit = {
    for (name <- Seq(Names.DirectClassWithoutNoArgConstructor,
        Names.IndirectClassWithoutNoArgConstructor)) {
      val instantiatableClass = Reflect.lookupInstantiatableClass(name).get

      assertThrows(classOf[InstantiationException], new Executable {
        override def execute(): Unit = instantiatableClass.newInstance()
      })
    }
  }

  @Test
  def constructorLookupMatchesPublicParameterTypes(): Unit = {
    for (name <- Seq(Names.DirectClass, Names.DirectClassWithoutNoArgConstructor,
        Names.IndirectClass, Names.IndirectClassWithoutNoArgConstructor)) {
      val instantiatableClass = Reflect.lookupInstantiatableClass(name).get

      val intStringConstructor =
        instantiatableClass.getConstructor(classOf[Int], classOf[String])
      assertTrue(intStringConstructor.isDefined, name)
      val intStringInstance = intStringConstructor.get
        .newInstance(543.asInstanceOf[AnyRef], "constructed")
        .asInstanceOf[Accessors]
      assertEquals(543, intStringInstance.x, name)
      assertEquals("constructed", intStringInstance.y, name)

      val intConstructor = instantiatableClass.getConstructor(classOf[Int])
      assertTrue(intConstructor.isDefined, name)
      val intInstance = intConstructor.get
        .newInstance(123.asInstanceOf[AnyRef])
        .asInstanceOf[Accessors]
      assertEquals(123, intInstance.x, name)
      assertEquals(name.stripPrefix(Names.Prefix), intInstance.y, name)

      val valueClassConstructor = instantiatableClass.getConstructor(classOf[Short])
      assertTrue(valueClassConstructor.isDefined, name)
      val valueClassInstance = valueClassConstructor.get
        .newInstance(21.toShort.asInstanceOf[AnyRef])
        .asInstanceOf[Accessors]
      assertEquals(42, valueClassInstance.x, name)
      assertEquals(name.stripPrefix(Names.Prefix), valueClassInstance.y, name)

      assertTrue(instantiatableClass.getConstructor(classOf[Boolean]).isEmpty, name)
      assertTrue(instantiatableClass.getConstructor(classOf[ValueClass]).isEmpty, name)
      assertTrue(instantiatableClass.getConstructor(classOf[Double]).isEmpty, name)
    }
  }

  @Test
  def declaredConstructorsExposeOnlyInvokablePublicConstructors(): Unit = {
    val instantiatableClass = Reflect.lookupInstantiatableClass(Names.DirectClass).get
    val parameterTypes = instantiatableClass.declaredConstructors
      .map(constructor => constructor.parameterTypes)
      .map(types => types.map(_.getName).toList)
      .toSet

    assertEquals(
      Set(
        List(classOf[Int].getName, classOf[String].getName),
        List(classOf[Int].getName),
        List(),
        List(classOf[Short].getName)),
      parameterTypes)
  }

  @Test
  def innerClassesCanBeInstantiatedWithTheOuterInstanceParameter(): Unit = {
    val outer = new PortableScalaReflectFixtures.ClassWithAnnotatedInnerClass(15)
    val instantiatableClass = Reflect.lookupInstantiatableClass(Names.InnerClass).get
    val constructor = instantiatableClass.getConstructor(outer.getClass, classOf[String])

    assertTrue(constructor.isDefined)
    val instance = constructor.get
      .newInstance(outer, "inner value")
      .asInstanceOf[Accessors]
    assertEquals(15, instance.x)
    assertEquals("inner value", instance.y)
  }

  @Test
  def annotatedLocalClassesAreNotDiscoverableByName(): Unit = {
    @EnableReflectiveInstantiation
    class AnnotatedLocalClass

    val localClassName = classOf[AnnotatedLocalClass].getName

    assertTrue(Reflect.lookupInstantiatableClass(localClassName).isEmpty)
  }

  @Test
  def loadModuleReturnsSingletonInstancesWithoutEagerInitialization(): Unit = {
    assertFalse(PortableScalaReflectFixtures.initializedModuleHasBeenInitialized)

    val loadableModuleClass = Reflect.lookupLoadableModuleClass(Names.InitializedModule).get

    assertFalse(PortableScalaReflectFixtures.initializedModuleHasBeenInitialized)
    loadableModuleClass.loadModule()
    assertTrue(PortableScalaReflectFixtures.initializedModuleHasBeenInitialized)
  }

  @Test
  def loadModuleReturnsAnnotatedDirectAndInheritedSingletons(): Unit = {
    val directInstance = Reflect.lookupLoadableModuleClass(Names.DirectModule).get
      .loadModule()
      .asInstanceOf[Accessors]
    val indirectInstance = Reflect.lookupLoadableModuleClass(Names.IndirectModule).get
      .loadModule()
      .asInstanceOf[Accessors]

    assertEquals(101, directInstance.x)
    assertEquals("DirectModule$", directInstance.y)
    assertEquals(101, indirectInstance.x)
    assertEquals("IndirectModule$", indirectInstance.y)
  }

  @Test
  def constructorFailuresAreReportedToTheCaller(): Unit = {
    val moduleClass = Reflect.lookupLoadableModuleClass(Names.ThrowingModule).get
    val moduleException = assertThrows(classOf[ArithmeticException], new Executable {
      override def execute(): Unit = moduleClass.loadModule()
    })
    assertEquals(ConstructorThrowsMessage, moduleException.getMessage)

    val instantiatableClass = Reflect.lookupInstantiatableClass(Names.ThrowingClass).get
    val classException = assertThrows(classOf[ArithmeticException], new Executable {
      override def execute(): Unit = instantiatableClass.newInstance()
    })
    assertEquals(ConstructorThrowsMessage, classException.getMessage)

    val constructorException = assertThrows(classOf[ArithmeticException], new Executable {
      override def execute(): Unit = instantiatableClass.getConstructor().get.newInstance()
    })
    assertEquals(ConstructorThrowsMessage, constructorException.getMessage)
  }

  @Test
  def privateNestedClassesCanBeInstantiatedWhenAnnotated(): Unit = {
    val instantiatableClass = Reflect.lookupInstantiatableClass(Names.PrivateNestedClass).get
    val instance = instantiatableClass.newInstance()

    assertEquals("instance of PrivateNestedClass", instance.toString)
  }

  @Test
  def innerSingletonObjectsAreInstantiatableButNotLoadableModules(): Unit = {
    val outer = new PortableScalaReflectFixtures.ClassWithAnnotatedInnerObject
    val instantiatableClass = Reflect.lookupInstantiatableClass(Names.InnerObject)

    assertTrue(Reflect.lookupLoadableModuleClass(Names.InnerObject).isEmpty)
    assertTrue(instantiatableClass.isDefined)

    val constructor = instantiatableClass.get.getConstructor(outer.getClass)
    assertTrue(constructor.isDefined)
    assertEquals(Names.InnerObject, constructor.get.newInstance(outer).getClass.getName)
  }

  @Test
  def lookupMethodsAcceptAnExplicitClassLoader(): Unit = {
    val classLoader = Thread.currentThread().getContextClassLoader

    assertTrue(Reflect.lookupInstantiatableClass(Names.DirectClass, classLoader).isDefined)
    assertTrue(Reflect.lookupLoadableModuleClass(Names.DirectModule, classLoader).isDefined)
  }
}

object PortableScalaReflectFixtures {
  final val ConstructorThrowsMessage = "constructor throws"

  trait Accessors {
    def x: Int
    def y: String
  }

  final class ValueClass(val self: Short) extends AnyVal

  @EnableReflectiveInstantiation
  class DirectClass(val x: Int, val y: String) extends Accessors {
    def this(x: Int) = this(x, "DirectClass")
    def this() = this(-1)
    def this(valueClass: ValueClass) = this(valueClass.self.toInt * 2)
    private def this(value: Double) = this(value.toInt)
  }

  @EnableReflectiveInstantiation
  class DirectClassWithoutNoArgConstructor(val x: Int, val y: String)
      extends Accessors {
    def this(x: Int) = this(x, "DirectClassWithoutNoArgConstructor")
    def this(valueClass: ValueClass) = this(valueClass.self.toInt * 2)
    private def this(value: Double) = this(value.toInt)
  }

  @EnableReflectiveInstantiation
  object DirectModule extends Accessors {
    val x = 101
    val y = "DirectModule$"
  }

  @EnableReflectiveInstantiation
  trait DirectTrait extends Accessors

  @EnableReflectiveInstantiation
  abstract class DirectAbstractClass(val x: Int, val y: String) extends Accessors

  @EnableReflectiveInstantiation
  class DirectClassWithoutPublicConstructor private (val x: Int, val y: String)
      extends Accessors

  class ClassWithAnnotatedInnerClass(outerX: Int) {
    @EnableReflectiveInstantiation
    class InnerClass(innerY: String) extends Accessors {
      val x = outerX
      val y = innerY
    }
  }

  @EnableReflectiveInstantiation
  trait EnablingTrait

  class IndirectClass(val x: Int, val y: String) extends EnablingTrait with Accessors {
    def this(x: Int) = this(x, "IndirectClass")
    def this() = this(-1)
    def this(valueClass: ValueClass) = this(valueClass.self.toInt * 2)
    private def this(value: Double) = this(value.toInt)
  }

  class IndirectClassWithoutNoArgConstructor(val x: Int, val y: String)
      extends EnablingTrait with Accessors {
    def this(x: Int) = this(x, "IndirectClassWithoutNoArgConstructor")
    def this(valueClass: ValueClass) = this(valueClass.self.toInt * 2)
    private def this(value: Double) = this(value.toInt)
  }

  object IndirectModule extends EnablingTrait with Accessors {
    val x = 101
    val y = "IndirectModule$"
  }

  trait IndirectTrait extends EnablingTrait with Accessors

  abstract class IndirectAbstractClass(val x: Int, val y: String)
      extends EnablingTrait with Accessors

  class IndirectClassWithoutPublicConstructor private (val x: Int, val y: String)
      extends EnablingTrait with Accessors

  class UnannotatedClass(val x: Int, val y: String) extends Accessors

  object UnannotatedModule extends Accessors {
    val x = 101
    val y = "UnannotatedModule$"
  }

  trait UnannotatedTrait extends Accessors

  var initializedModuleHasBeenInitialized: Boolean = false

  @EnableReflectiveInstantiation
  object InitializedModule {
    initializedModuleHasBeenInitialized = true
  }

  @EnableReflectiveInstantiation
  object ThrowingModule {
    throw new ArithmeticException(ConstructorThrowsMessage)
  }

  @EnableReflectiveInstantiation
  class ThrowingClass {
    throw new ArithmeticException(ConstructorThrowsMessage)
  }

  class ClassWithAnnotatedInnerObject {
    @EnableReflectiveInstantiation
    object InnerObject
  }

  @EnableReflectiveInstantiation
  private class PrivateNestedClass {
    override def toString: String = "instance of PrivateNestedClass"
  }
}

object Names {
  final val Prefix =
    "org_portable_scala.portable_scala_reflect_2_13.PortableScalaReflectFixtures$"

  final val DirectClass = Prefix + "DirectClass"
  final val DirectClassWithoutNoArgConstructor =
    Prefix + "DirectClassWithoutNoArgConstructor"
  final val DirectModule = Prefix + "DirectModule$"
  final val DirectTrait = Prefix + "DirectTrait"
  final val DirectAbstractClass = Prefix + "DirectAbstractClass"
  final val DirectClassWithoutPublicConstructor = Prefix + "DirectClassWithoutPublicConstructor"
  final val InnerClass = Prefix + "ClassWithAnnotatedInnerClass$InnerClass"
  final val IndirectClass = Prefix + "IndirectClass"
  final val IndirectClassWithoutNoArgConstructor =
    Prefix + "IndirectClassWithoutNoArgConstructor"
  final val IndirectModule = Prefix + "IndirectModule$"
  final val IndirectTrait = Prefix + "IndirectTrait"
  final val IndirectAbstractClass = Prefix + "IndirectAbstractClass"
  final val IndirectClassWithoutPublicConstructor =
    Prefix + "IndirectClassWithoutPublicConstructor"
  final val UnannotatedClass = Prefix + "UnannotatedClass"
  final val UnannotatedModule = Prefix + "UnannotatedModule$"
  final val UnannotatedTrait = Prefix + "UnannotatedTrait"
  final val InitializedModule = Prefix + "InitializedModule$"
  final val ThrowingModule = Prefix + "ThrowingModule$"
  final val ThrowingClass = Prefix + "ThrowingClass"
  final val InnerObject = Prefix + "ClassWithAnnotatedInnerObject$InnerObject$"
  final val PrivateNestedClass = Prefix + "PrivateNestedClass"
  final val MissingClass = Prefix + "MissingClass"
}
