/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_portable_scala.portable_scala_reflect_2_13

import org.assertj.core.api.Assertions.{assertThat, assertThatThrownBy}
import org.assertj.core.api.ThrowableAssert.ThrowingCallable
import org.junit.jupiter.api.Test
import org.portablescala.reflect.Reflect
import org.portablescala.reflect.annotation.EnableReflectiveInstantiation

class Portable_scala_reflect_2_13Test {
  private val loader: ClassLoader = getClass.getClassLoader
  private val testPackageName: String = getClass.getPackage.getName

  @Test
  def instantiatesAnnotatedClassesWithPublicConstructors(): Unit = {
    val reflected = requireFound(
      Reflect.lookupInstantiatableClass(classOf[AnnotatedMultiConstructorService].getName, loader),
      "annotated class should be discoverable")

    assertThat(reflected.runtimeClass).isEqualTo(classOf[AnnotatedMultiConstructorService])
    val signatures: List[List[Class[_]]] = reflected.declaredConstructors.map(_.parameterTypes)
    assertThat(signatures.contains(List.empty[Class[_]])).isTrue()
    assertThat(signatures.contains(List(classOf[Int]))).isTrue()
    assertThat(signatures.contains(List(classOf[String], classOf[Int]))).isTrue()

    val defaultInstance = reflected.newInstance().asInstanceOf[ReflectiveAccessors]
    assertThat(defaultInstance.x).isEqualTo(1)
    assertThat(defaultInstance.y).isEqualTo("default")

    val stringIntConstructor = requireFound(
      reflected.getConstructor(classOf[String], classOf[Int]),
      "String and Int constructor should be exposed")
    val stringIntInstance = stringIntConstructor
      .newInstance("custom", Int.box(42))
      .asInstanceOf[ReflectiveAccessors]
    assertThat(stringIntConstructor.parameterTypes)
      .isEqualTo(List(classOf[String], classOf[Int]))
    assertThat(stringIntInstance.x).isEqualTo(42)
    assertThat(stringIntInstance.y).isEqualTo("custom")

    val intConstructor = requireFound(
      reflected.getConstructor(classOf[Int]),
      "Int constructor should be exposed")
    val intInstance = intConstructor.newInstance(Int.box(7)).asInstanceOf[ReflectiveAccessors]
    assertThat(intInstance.x).isEqualTo(7)
    assertThat(intInstance.y).isEqualTo("count")

    assertThat(reflected.getConstructor(classOf[Boolean]).isEmpty).isTrue()
  }

  @Test
  def discoversClassesThroughInheritedAnnotations(): Unit = {
    val traitEnabled = requireFound(
      Reflect.lookupInstantiatableClass(classOf[TraitEnabledService].getName, loader),
      "annotation inherited from a trait should enable lookup")
      .newInstance()
      .asInstanceOf[TraitEnabledService]
    assertThat(traitEnabled.description).isEqualTo("enabled-by-trait")

    val superclassEnabled = requireFound(
      Reflect.lookupInstantiatableClass(classOf[SuperclassEnabledService].getName, loader),
      "annotation inherited from a superclass should enable lookup")
      .newInstance()
      .asInstanceOf[SuperclassEnabledService]
    assertThat(superclassEnabled.description).isEqualTo("enabled-by-superclass")
  }

  @Test
  def rejectsTypesThatAreNotInstantiatableThroughPortableReflect(): Unit = {
    assertThat(Reflect.lookupInstantiatableClass(classOf[UnannotatedService].getName, loader).isEmpty)
      .isTrue()
    assertThat(Reflect.lookupInstantiatableClass(classOf[AbstractEnabledService].getName, loader).isEmpty)
      .isTrue()
    assertThat(Reflect.lookupInstantiatableClass(classOf[NoPublicConstructorService].getName, loader).isEmpty)
      .isTrue()

    @EnableReflectiveInstantiation
    class MethodLocalEnabledService

    assertThat(Reflect.lookupInstantiatableClass(classOf[MethodLocalEnabledService].getName, loader).isEmpty)
      .isTrue()
  }

  @Test
  def reportsMissingZeroArgumentConstructorAsInstantiationFailure(): Unit = {
    val reflected = requireFound(
      Reflect.lookupInstantiatableClass(classOf[RequiredArgumentService].getName, loader),
      "class with public non-empty constructor should still be discoverable")

    val constructor = requireFound(
      reflected.getConstructor(classOf[String]),
      "required String constructor should be exposed")
    val instance = constructor.newInstance("required").asInstanceOf[RequiredArgumentService]
    assertThat(instance.value).isEqualTo("required")

    assertThatThrownBy(throwing {
      reflected.newInstance()
    }).isInstanceOf(classOf[InstantiationException])
  }

  @Test
  def unwrapsExceptionsThrownByClassConstructors(): Unit = {
    val reflected = requireFound(
      Reflect.lookupInstantiatableClass(classOf[ThrowingConstructorService].getName, loader),
      "throwing constructor class should be discoverable")

    assertThatThrownBy(throwing {
      reflected.newInstance()
    }).isInstanceOf(classOf[ArithmeticException])
      .hasMessage("constructor failed")

    val constructor = requireFound(
      reflected.getConstructor(),
      "zero-argument throwing constructor should be exposed")
    assertThatThrownBy(throwing {
      constructor.newInstance()
    }).isInstanceOf(classOf[ArithmeticException])
      .hasMessage("constructor failed")
  }

  @Test
  def instantiatesAnnotatedInnerClassesWithTheirOuterInstance(): Unit = {
    val owner = new InnerServiceOwner(13)
    val className = new owner.InnerEnabledService("direct").getClass.getName
    val reflected = requireFound(
      Reflect.lookupInstantiatableClass(className, loader),
      "annotated member class should be discoverable")

    val constructor = requireFound(
      reflected.getConstructor(owner.getClass, classOf[String]),
      "inner class constructor should include the outer instance")
    val instance = constructor
      .newInstance(owner, "created")
      .asInstanceOf[ReflectiveAccessors]

    assertThat(instance.x).isEqualTo(13)
    assertThat(instance.y).isEqualTo("created")
  }

  @Test
  def loadsAnnotatedModulesAndDelaysInitializationUntilLoadModule(): Unit = {
    InitializationTracker.moduleLoads = 0
    val moduleName = topLevelModuleName("AnnotatedUtilityModule")
    val reflected = requireFound(
      Reflect.lookupLoadableModuleClass(moduleName, loader),
      "annotated top-level object should be discoverable as a module class")

    assertThat(reflected.runtimeClass.getName).isEqualTo(moduleName)
    assertThat(InitializationTracker.moduleLoads).isZero()

    val module = reflected.loadModule().asInstanceOf[ModuleApi]
    assertThat(InitializationTracker.moduleLoads).isEqualTo(1)
    assertThat(module.describe("portable")).isEqualTo("module:portable")
    assertThat(module).isSameAs(AnnotatedUtilityModule)
  }

  @Test
  def loadsModulesThroughInheritedAnnotationsAndRejectsInvalidModules(): Unit = {
    val inheritedModule = requireFound(
      Reflect.lookupLoadableModuleClass(topLevelModuleName("InheritedEnabledModule"), loader),
      "annotation inherited by an object should enable module lookup")
      .loadModule()
      .asInstanceOf[ModuleApi]
    assertThat(inheritedModule.describe("portable")).isEqualTo("inherited:portable")

    assertThat(Reflect.lookupLoadableModuleClass(topLevelModuleName("UnannotatedModule"), loader).isEmpty)
      .isTrue()
    assertThat(Reflect.lookupLoadableModuleClass(classOf[AnnotatedMultiConstructorService].getName, loader).isEmpty)
      .isTrue()
    assertThat(Reflect.lookupInstantiatableClass(topLevelModuleName("AnnotatedUtilityModule"), loader).isEmpty)
      .isTrue()
  }

  @Test
  def unwrapsExceptionsThrownWhileLoadingModules(): Unit = {
    val reflected = requireFound(
      Reflect.lookupLoadableModuleClass(topLevelModuleName("ThrowingModule"), loader),
      "throwing module should be discoverable before it is loaded")

    assertThatThrownBy(throwing {
      reflected.loadModule()
    }).isInstanceOf(classOf[IllegalStateException])
      .hasMessage("module failed")
  }

  @Test
  def currentClassLoaderMacroOverloadsDelegateToExplicitLookup(): Unit = {
    val className = classOf[AnnotatedMultiConstructorService].getName
    val classFromMacroOverload = requireFound(
      Reflect.lookupInstantiatableClass(className),
      "macro overload should use the enclosing test class loader")
    assertThat(classFromMacroOverload.runtimeClass).isEqualTo(classOf[AnnotatedMultiConstructorService])

    val moduleName = topLevelModuleName("InheritedEnabledModule")
    val moduleFromMacroOverload = requireFound(
      Reflect.lookupLoadableModuleClass(moduleName),
      "macro overload should use the enclosing test class loader for modules")
    assertThat(moduleFromMacroOverload.runtimeClass.getName).isEqualTo(moduleName)
  }

  private def topLevelModuleName(simpleName: String): String =
    s"$testPackageName.$simpleName$$"

  private def requireFound[A](result: Option[A], message: String): A = {
    assertThat(result.isDefined).as(message).isTrue()
    result.get
  }

  private def throwing(block: => Any): ThrowingCallable = new ThrowingCallable {
    override def call(): Unit = {
      block
      ()
    }
  }
}

trait ReflectiveAccessors {
  def x: Int
  def y: String
}

trait ModuleApi {
  def describe(value: String): String
}

@EnableReflectiveInstantiation
class AnnotatedMultiConstructorService(val y: String, val x: Int) extends ReflectiveAccessors {
  def this() = this("default", 1)
  def this(x: Int) = this("count", x)
  private def this(enabled: Boolean) = this("private", if (enabled) 1 else 0)
}

@EnableReflectiveInstantiation
trait ReflectivelyEnabledTrait

class TraitEnabledService extends ReflectivelyEnabledTrait {
  def description: String = "enabled-by-trait"
}

@EnableReflectiveInstantiation
abstract class ReflectivelyEnabledBase

class SuperclassEnabledService extends ReflectivelyEnabledBase {
  def description: String = "enabled-by-superclass"
}

class UnannotatedService

@EnableReflectiveInstantiation
abstract class AbstractEnabledService

@EnableReflectiveInstantiation
class NoPublicConstructorService private ()

@EnableReflectiveInstantiation
class RequiredArgumentService(val value: String)

@EnableReflectiveInstantiation
class ThrowingConstructorService {
  throw new ArithmeticException("constructor failed")
}

class InnerServiceOwner(ownerValue: Int) {
  @EnableReflectiveInstantiation
  class InnerEnabledService(innerValue: String) extends ReflectiveAccessors {
    override val x: Int = ownerValue
    override val y: String = innerValue
  }
}

object InitializationTracker {
  var moduleLoads: Int = 0
}

@EnableReflectiveInstantiation
object AnnotatedUtilityModule extends ModuleApi {
  InitializationTracker.moduleLoads += 1

  override def describe(value: String): String = s"module:$value"
}

@EnableReflectiveInstantiation
trait ReflectivelyEnabledModuleApi extends ModuleApi

object InheritedEnabledModule extends ReflectivelyEnabledModuleApi {
  override def describe(value: String): String = s"inherited:$value"
}

object UnannotatedModule extends ModuleApi {
  override def describe(value: String): String = s"unannotated:$value"
}

@EnableReflectiveInstantiation
object ThrowingModule {
  throw new IllegalStateException("module failed")
}
