/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_specs2.specs2_common_3

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.specs2.control.Operation
import org.specs2.control.runOperation
import org.specs2.reflect.Classes
import org.specs2.time.SimpleTimer

class ClassesTest {
  private val loader: ClassLoader = classOf[ClassesTest].getClassLoader

  @Test
  def loadClassEitherAndExistsClassUseSuppliedLoader(): Unit = {
    val loaded: Either[Throwable, Class[ZeroArgumentTarget]] =
      runSuccessfully(Classes.loadClassEither[ZeroArgumentTarget](classOf[ZeroArgumentTarget].getName, loader))
    val existingClassFound: Boolean =
      runSuccessfully(Classes.existsClass(classOf[ZeroArgumentTarget].getName, loader))
    val missingClassFound: Boolean =
      runSuccessfully(Classes.existsClass("org_specs2.specs2_common_3.DoesNotExist", loader))

    loaded match {
      case Right(klass) => assertThat(klass).isEqualTo(classOf[ZeroArgumentTarget])
      case Left(error)  => fail(s"Expected class loading to succeed, but got $error")
    }
    assertThat(existingClassFound).isTrue()
    assertThat(missingClassFound).isFalse()
  }

  @Test
  def createInstanceFromClassUsesZeroArgumentConstructor(): Unit = {
    val instance: ZeroArgumentTarget =
      runSuccessfully(Classes.createInstanceFromClass(classOf[ZeroArgumentTarget], loader))

    assertThat(instance.message).isEqualTo("created with no arguments")
  }

  @Test
  def createInstanceEitherUsesSpecs2LibraryClassConstructor(): Unit = {
    val created: Either[Throwable, SimpleTimer] =
      runSuccessfully(Classes.createInstanceEither[SimpleTimer](classOf[SimpleTimer].getName, loader))

    created match {
      case Right(instance) => assertThat(instance).isInstanceOf(classOf[SimpleTimer])
      case Left(error)     => fail(s"Expected specs2 library instance creation to succeed, but got $error")
    }
  }

  @Test
  def createInstanceEitherUsesAvailableConstructor(): Unit = {
    val created: Either[Throwable, ZeroArgumentTarget] =
      runSuccessfully(Classes.createInstanceEither[ZeroArgumentTarget](classOf[ZeroArgumentTarget].getName, loader))

    created match {
      case Right(instance) => assertThat(instance.message).isEqualTo("created with no arguments")
      case Left(error)     => fail(s"Expected instance creation to succeed, but got $error")
    }
  }

  @Test
  def createInstanceEitherFailsOnlyAfterInspectingLibraryTraitConstructors(): Unit = {
    val result: Either[?, Either[Throwable, org.specs2.reflect.Classes]] =
      runOperation(
        Classes.createInstanceEither[org.specs2.reflect.Classes](
          classOf[org.specs2.reflect.Classes].getName,
          loader
        )
      )

    assertThat(result.isLeft).isTrue()
  }

  @Test
  def createInstanceEitherReturnsSpecs2SingletonModuleInstance(): Unit = {
    val created: Either[Throwable, AnyRef] =
      runSuccessfully(Classes.createInstanceEither[AnyRef](Classes.getClass.getName, loader))

    created match {
      case Right(instance) => assertThat(instance).isSameAs(Classes)
      case Left(error)     => fail(s"Expected specs2 Classes singleton creation to succeed, but got $error")
    }
  }

  @Test
  def createInstanceEitherUsesProvidedDefaultConstructorParameter(): Unit = {
    val dependency: ProvidedDependency = new ProvidedDependency("provided through createInstanceEither")
    val created: Either[Throwable, RequiresProvidedParameter] =
      runSuccessfully(
        Classes.createInstanceEither[RequiresProvidedParameter](
          classOf[RequiresProvidedParameter].getName,
          loader,
          List(dependency)
        )
      )

    created match {
      case Right(instance) => assertThat(instance.dependency).isSameAs(dependency)
      case Left(error)     => fail(s"Expected parameterized instance creation to succeed, but got $error")
    }
  }

  @Test
  def createInstanceFromClassReturnsScalaObjectModuleInstance(): Unit = {
    val singletonClass: Class[AnyRef] = SingletonTarget.getClass.asInstanceOf[Class[AnyRef]]
    val instance: AnyRef = runSuccessfully(Classes.createInstanceFromClass[AnyRef](singletonClass, loader))

    assertThat(instance).isSameAs(SingletonTarget)
  }

  @Test
  def createInstanceFromClassBuildsMissingConstructorParameter(): Unit = {
    val instance: RequiresConstructibleParameter =
      runSuccessfully(Classes.createInstanceFromClass(classOf[RequiresConstructibleParameter], loader))

    assertThat(instance.dependency).isNotNull()
    assertThat(instance.dependency.token).isEqualTo("constructed dependency")
  }

  @Test
  def createInstanceFromClassUsesProvidedDefaultConstructorParameter(): Unit = {
    val dependency: ProvidedDependency = new ProvidedDependency("provided dependency")
    val instance: RequiresProvidedParameter =
      runSuccessfully(Classes.createInstanceFromClass(classOf[RequiresProvidedParameter], loader, List(dependency)))

    assertThat(instance.dependency).isSameAs(dependency)
    assertThat(instance.dependency.token).isEqualTo("provided dependency")
  }

  private def runSuccessfully[A](operation: Operation[A]): A =
    runOperation(operation) match {
      case Right(value) => value
      case Left(error)  => fail(s"Expected specs2 operation to succeed, but got $error")
    }
}

final class ZeroArgumentTarget {
  val message: String = "created with no arguments"
}

object SingletonTarget {
  val message: String = "singleton module"
}

final class ConstructibleDependency {
  val token: String = "constructed dependency"
}

final class RequiresConstructibleParameter(val dependency: ConstructibleDependency)

final class ProvidedDependency(val token: String)

final class RequiresProvidedParameter(val dependency: ProvidedDependency)
