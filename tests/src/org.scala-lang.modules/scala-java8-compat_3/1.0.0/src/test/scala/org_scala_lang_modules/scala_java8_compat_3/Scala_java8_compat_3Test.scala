/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang_modules.scala_java8_compat_3

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

import java.time.{Duration => JavaDuration}
import java.util.Optional
import java.util.OptionalDouble
import java.util.OptionalInt
import java.util.OptionalLong
import java.util.PrimitiveIterator
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import scala.compat.java8.DurationConverters
import scala.compat.java8.DurationConverters._
import scala.compat.java8.FunctionConverters._
import scala.compat.java8.FutureConverters
import scala.compat.java8.FutureConverters._
import scala.compat.java8.OptionConverters
import scala.compat.java8.OptionConverters._
import scala.compat.java8.PrimitiveIteratorConverters._
import scala.compat.java8.StreamConverters._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

class Scala_java8_compat_3Test {
  @Test
  def convertsScalaOptionsAndJavaOptionalsIncludingPrimitiveSpecializations(): Unit = {
    val javaOption: Optional[String] = Some("scala").asJava
    assertThat(javaOption.isPresent).isTrue()
    assertThat(javaOption.get()).isEqualTo("scala")
    assertThat((None: Option[String]).asJava.isPresent).isFalse()
    assertThat(Optional.of("java").asScala).isEqualTo(Some("java"))
    assertThat(Optional.empty[String]().asScala).isEqualTo(None)

    val optionalInt: OptionalInt = Some(42).asPrimitive[OptionalInt]
    val optionalLong: OptionalLong = Some(9876543210L).asPrimitive[OptionalLong]
    val optionalDouble: OptionalDouble = Some(2.5d).asPrimitive[OptionalDouble]
    assertThat(optionalInt.getAsInt).isEqualTo(42)
    assertThat(optionalLong.getAsLong).isEqualTo(9876543210L)
    assertThat(optionalDouble.getAsDouble).isEqualTo(2.5d)
    assertThat(Optional.of(7).asPrimitive[OptionalInt].getAsInt).isEqualTo(7)
    assertThat(OptionalInt.empty().asScala).isEqualTo(None)
    assertThat(OptionalLong.of(9L).asGeneric.get()).isEqualTo(9L)
    assertThat(OptionalDouble.of(3.25d).asScala).isEqualTo(Some(3.25d))

    assertThat(OptionConverters.toJava(Some("direct")).get()).isEqualTo("direct")
    assertThat(OptionConverters.toScala(Optional.of("roundtrip"))).isEqualTo(Some("roundtrip"))
    assertThat(OptionConverters.toScala(OptionalInt.of(11))).isEqualTo(Some(11))
    assertThat(OptionConverters.toScala(OptionalLong.of(12L))).isEqualTo(Some(12L))
    assertThat(OptionConverters.toScala(OptionalDouble.of(13.5d))).isEqualTo(Some(13.5d))
  }

  @Test
  def convertsJavaAndScalaDurationsWithExtensionAndStaticMethods(): Unit = {
    assertThat(JavaDuration.ZERO.toScala).isEqualTo(Duration.Zero)
    assertThat(JavaDuration.ofSeconds(5).toScala).isEqualTo(5.seconds)
    assertThat(JavaDuration.ofSeconds(2, 500).toScala).isEqualTo(FiniteDuration(2000000500L, TimeUnit.NANOSECONDS))
    assertThat(JavaDuration.ofNanos(750).toScala).isEqualTo(750.nanos)

    assertThat(1500.millis.toJava).isEqualTo(JavaDuration.ofMillis(1500))
    assertThat(DurationConverters.toJava(2.days)).isEqualTo(JavaDuration.ofDays(2))
    assertThat(DurationConverters.toScala(JavaDuration.ofMinutes(3))).isEqualTo(3.minutes)
    assertThat((-250).millis.toJava).isEqualTo(JavaDuration.ofMillis(-250))

    val durationFailure = assertThrows(classOf[IllegalArgumentException], () => DurationConverters.toScala(JavaDuration.ofSeconds(Long.MaxValue, 1)))
    assertThat(durationFailure.getMessage).contains("cannot be expressed")
  }

  @Test
  def bridgesScalaFuturesAndJavaCompletionStagesInBothDirections(): Unit = {
    val promise = FutureConverters.promise[String]()
    val stage = promise.future.toJava
    assertThat(promise.success("completed")).isEqualTo(promise)
    assertThat(Await.result(stage.toScala, 2.seconds)).isEqualTo("completed")

    val completedStage = CompletableFuture.completedFuture("java")
    val mappedFuture = completedStage.toScala.map(_.toUpperCase)(ExecutionContext.global)
    assertThat(Await.result(mappedFuture, 2.seconds)).isEqualTo("JAVA")

    val failedStage = new CompletableFuture[String]()
    val failure = new IllegalStateException("boom")
    failedStage.completeExceptionally(failure)
    val stageFailure = assertThrows(classOf[IllegalStateException], () => Await.result(failedStage.toScala, 2.seconds))
    assertThat(stageFailure.getMessage).isEqualTo("boom")

    assertThat(Await.result(FutureConverters.keptPromise("kept").future, 2.seconds)).isEqualTo("kept")
    val promiseFailure = assertThrows(
      classOf[RuntimeException],
      () => Await.result(FutureConverters.failedPromise[String](new RuntimeException("failed")).future, 2.seconds)
    )
    assertThat(promiseFailure.getMessage).isEqualTo("failed")
  }

  @Test
  def createsExecutionContextsFromJavaExecutorsAndReportsFailures(): Unit = {
    val executorService = Executors.newSingleThreadExecutor()
    val executionContext = FutureConverters.fromExecutorService(executorService)
    try {
      val latch = new CountDownLatch(1)
      executionContext.execute(new Runnable {
        override def run(): Unit = latch.countDown()
      })
      assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue()
    } finally {
      executionContext.shutdown()
      assertThat(executionContext.awaitTermination(2, TimeUnit.SECONDS)).isTrue()
    }

    val reported = new AtomicReference[Throwable]()
    val sameThreadExecutor = new Executor {
      override def execute(command: Runnable): Unit = command.run()
    }
    val reporter = new Consumer[Throwable] {
      override def accept(error: Throwable): Unit = reported.set(error)
    }
    val directExecutionContext = FutureConverters.fromExecutor(sameThreadExecutor, reporter)
    val reportedFailure = new IllegalArgumentException("reported")
    directExecutionContext.reportFailure(reportedFailure)
    assertThat(reported.get()).isSameAs(reportedFailure)
  }

  @Test
  def convertsScalaAndJavaIteratorsToPrimitiveIterators(): Unit = {
    val ints: PrimitiveIterator.OfInt = Iterator(1, 2, 3).asPrimitive[PrimitiveIterator.OfInt]
    assertThat(ints.nextInt()).isEqualTo(1)
    assertThat(ints.nextInt()).isEqualTo(2)
    assertThat(ints.nextInt()).isEqualTo(3)
    assertThat(ints.hasNext).isFalse()

    val longs: PrimitiveIterator.OfLong = Iterator(4L, 5L).asPrimitive[PrimitiveIterator.OfLong]
    val longSum = new AtomicReference[Long](0L)
    longs.forEachRemaining(new java.util.function.LongConsumer {
      override def accept(value: Long): Unit = longSum.set(longSum.get() + value)
    })
    assertThat(longSum.get()).isEqualTo(9L)

    val javaDoubles = new java.util.ArrayList[Double]()
    javaDoubles.add(1.25d)
    javaDoubles.add(2.75d)
    val doubles: PrimitiveIterator.OfDouble = javaDoubles.iterator().asPrimitive[PrimitiveIterator.OfDouble]
    assertThat(doubles.nextDouble()).isEqualTo(1.25d)
    assertThat(doubles.nextDouble()).isEqualTo(2.75d)
    assertThat(doubles.hasNext).isFalse()

    val javaInts = new java.util.ArrayList[Int]()
    javaInts.add(8)
    javaInts.add(13)
    val fromJava: PrimitiveIterator.OfInt = javaInts.iterator().asPrimitive[PrimitiveIterator.OfInt]
    assertThat(fromJava.nextInt()).isEqualTo(8)
    assertThat(fromJava.nextInt()).isEqualTo(13)
  }

  @Test
  def createsJavaStreamsFromScalaCollectionsArraysMapsAndSteppers(): Unit = {
    assertThat(Vector(1, 2, 3, 4).seqStream.sum()).isEqualTo(10)
    assertThat(Vector(1L, 2L, 3L).parStream.sum()).isEqualTo(6L)
    assertThat(List("a", "bb", "ccc").seqStream.mapToInt(_.length).sum()).isEqualTo(6)

    assertThat(Array(2, 3, 5).seqStream.sum()).isEqualTo(10)
    assertThat(Array(2L, 3L, 5L).parStream.sum()).isEqualTo(10L)
    assertThat(Array(1.5d, 2.5d).seqStream.sum()).isEqualTo(4.0d)
    assertThat(Array[Byte](1, 2, 3).seqStream.sum()).isEqualTo(6)
    assertThat(Array[Short](4, 5).seqStream.sum()).isEqualTo(9)
    assertThat(Array('a', 'b').seqStream.sum()).isEqualTo('a'.toInt + 'b'.toInt)
    assertThat(Array(1.25f, 2.75f).seqStream.sum()).isEqualTo(4.0d)
    assertThat(Array("left", "right").seqStream.mapToInt(_.length).sum()).isEqualTo(9)

    val numberNames = Map(1 -> "one", 2 -> "two", 3 -> "three")
    assertThat(numberNames.seqKeyStream.sum()).isEqualTo(6)
    assertThat(numberNames.seqValueStream.toScala[List].sorted).isEqualTo(List("one", "three", "two"))
    assertThat(numberNames.seqStream.mapToInt(entry => entry._1 + entry._2.length).sum()).isEqualTo(17)

    assertThat(Vector(2, 4, 6, 8).stepper.count(_ > 3)).isEqualTo(3L)
    assertThat(Vector(2, 4, 6, 8).stepper.find(_ == 6)).isEqualTo(Some(6))
    assertThat(Vector(2, 4, 6, 8).stepper.exists(_ == 5)).isFalse()
    assertThat(Vector(2, 4, 6, 8).stepper.fold(0)(_ + _)).isEqualTo(20)
    assertThat(Vector(2, 4, 6, 8).stepper.foldTo(0)(_ + _)(_ >= 6)).isEqualTo(6)
    assertThat(Vector(2, 4, 6, 8).stepper.reduce(_ + _)).isEqualTo(20)
  }

  @Test
  def convertsJavaStreamsBackToScalaCollectionsAndPrimitiveStreams(): Unit = {
    val words = java.util.stream.Stream.of("alpha", "beta", "gamma").toScala[Vector]
    assertThat(words).isEqualTo(Vector("alpha", "beta", "gamma"))

    val intValues = java.util.stream.IntStream.of(1, 1, 2, 3, 5).toScala[List]
    assertThat(intValues).isEqualTo(List(1, 1, 2, 3, 5))

    val longValues = java.util.stream.LongStream.of(10L, 20L, 30L).toScala[Vector]
    assertThat(longValues).isEqualTo(Vector(10L, 20L, 30L))

    val doubleValues = java.util.stream.DoubleStream.of(0.5d, 1.5d, 2.5d).toScala[List]
    assertThat(doubleValues).isEqualTo(List(0.5d, 1.5d, 2.5d))

    val unboxedIntSum = java.util.stream.Stream
      .of[java.lang.Integer](Integer.valueOf(3), Integer.valueOf(4), Integer.valueOf(5))
      .unboxed
      .sum()
    assertThat(unboxedIntSum).isEqualTo(12)

    val accumulated = java.util.stream.IntStream.of(7, 8, 9).accumulate
    assertThat(accumulated.toList).isEqualTo(List(7, 8, 9))

    val parallelResult = java.util.stream.Stream.of("x", "yy", "zzz").parallel().toScala[List].sorted
    assertThat(parallelResult).isEqualTo(List("x", "yy", "zzz"))
  }

  @Test
  def convertsScalaFunctionsToJavaFunctionalInterfacesAndBack(): Unit = {
    val javaFunction = asJavaFunction[String, Int]((value: String) => value.length)
    assertThat(javaFunction.apply("scala")).isEqualTo(5)

    val reversingFunction = new java.util.function.Function[String, String] {
      override def apply(value: String): String = value.reverse
    }
    val scalaFunction = asScalaFromFunction(reversingFunction)
    assertThat(scalaFunction("compat")).isEqualTo("tapmoc")

    val consumed = new AtomicReference[String]()
    val javaConsumer = asJavaConsumer[String]((value: String) => consumed.set(value))
    javaConsumer.accept("accepted")
    assertThat(consumed.get()).isEqualTo("accepted")

    val scalaConsumer = asScalaFromConsumer(new java.util.function.Consumer[String] {
      override def accept(value: String): Unit = consumed.set(value.toUpperCase)
    })
    scalaConsumer("converted")
    assertThat(consumed.get()).isEqualTo("CONVERTED")

    val predicate = asJavaPredicate[String]((value: String) => value.startsWith("s"))
    assertThat(predicate.test("scala")).isTrue()
    assertThat(predicate.test("java")).isFalse()

    val supplier = asJavaSupplier[String](() => "supplied")
    assertThat(supplier.get()).isEqualTo("supplied")
    val scalaSupplier = asScalaFromSupplier(new java.util.function.Supplier[Int] {
      override def get(): Int = 99
    })
    assertThat(scalaSupplier()).isEqualTo(99)

    val biFunction = asJavaBiFunction[String, String, String]((left: String, right: String) => left + right)
    assertThat(biFunction.apply("java", "8")).isEqualTo("java8")
    val binaryOperator = asJavaBinaryOperator[String]((left: String, right: String) => if (left.compareTo(right) <= 0) left else right)
    assertThat(binaryOperator.apply("scala", "java")).isEqualTo("java")
    val unaryOperator = asJavaUnaryOperator[String]((value: String) => value.take(1).toUpperCase + value.drop(1))
    assertThat(unaryOperator.apply("scala")).isEqualTo("Scala")
  }

  @Test
  def convertsPrimitiveJavaFunctionalInterfacesAndScalaFunctions(): Unit = {
    val intUnary = asJavaIntUnaryOperator((value: Int) => value + 1)
    assertThat(intUnary.applyAsInt(41)).isEqualTo(42)
    val intPredicate = asJavaIntPredicate((value: Int) => value % 2 == 0)
    assertThat(intPredicate.test(10)).isTrue()
    val intToDouble = asJavaIntToDoubleFunction((value: Int) => value / 2.0d)
    assertThat(intToDouble.applyAsDouble(9)).isEqualTo(4.5d)
    val intBinary = asJavaIntBinaryOperator((left: Int, right: Int) => left * right)
    assertThat(intBinary.applyAsInt(6, 7)).isEqualTo(42)

    val longUnary = asJavaLongUnaryOperator((value: Long) => value * 2L)
    assertThat(longUnary.applyAsLong(21L)).isEqualTo(42L)
    val longToInt = asJavaLongToIntFunction((value: Long) => value.toInt + 1)
    assertThat(longToInt.applyAsInt(41L)).isEqualTo(42)
    val longSupplier = asJavaLongSupplier(() => 123L)
    assertThat(longSupplier.getAsLong).isEqualTo(123L)

    val doubleUnary = asJavaDoubleUnaryOperator((value: Double) => value + 0.5d)
    assertThat(doubleUnary.applyAsDouble(1.25d)).isEqualTo(1.75d)
    val doubleToLong = asJavaDoubleToLongFunction((value: Double) => Math.round(value))
    assertThat(doubleToLong.applyAsLong(41.6d)).isEqualTo(42L)
    val doubleSupplier = asJavaDoubleSupplier(() => 6.25d)
    assertThat(doubleSupplier.getAsDouble).isEqualTo(6.25d)

    val booleanSupplier = asJavaBooleanSupplier(() => true)
    assertThat(booleanSupplier.getAsBoolean).isTrue()

    val scalaIntPredicate = asScalaFromIntPredicate(new java.util.function.IntPredicate {
      override def test(value: Int): Boolean = value > 10
    })
    assertThat(scalaIntPredicate(11)).isTrue()
    assertThat(scalaIntPredicate(10)).isFalse()

    val scalaLongBinary = asScalaFromLongBinaryOperator(new java.util.function.LongBinaryOperator {
      override def applyAsLong(left: Long, right: Long): Long = left + right
    })
    assertThat(scalaLongBinary(20L, 22L)).isEqualTo(42L)

    val scalaDoubleFunction = asScalaFromDoubleFunction(new java.util.function.DoubleFunction[String] {
      override def apply(value: Double): String = f"$value%.1f"
    })
    assertThat(scalaDoubleFunction(2.5d)).isEqualTo("2.5")
  }

  @Test
  def adaptsJavaObjectPrimitiveConsumersAndNumericFunctions(): Unit = {
    val counter = new AtomicInteger(0)
    val objIntConsumer = asJavaObjIntConsumer[String]((prefix: String, value: Int) => counter.addAndGet(prefix.length + value))
    objIntConsumer.accept("ab", 3)
    assertThat(counter.get()).isEqualTo(5)

    val objLongConsumer = asJavaObjLongConsumer[String]((prefix: String, value: Long) => counter.addAndGet(prefix.length + value.toInt))
    objLongConsumer.accept("abc", 4L)
    assertThat(counter.get()).isEqualTo(12)

    val objDoubleConsumer = asJavaObjDoubleConsumer[String]((prefix: String, value: Double) => counter.addAndGet(prefix.length + value.toInt))
    objDoubleConsumer.accept("a", 2.9d)
    assertThat(counter.get()).isEqualTo(15)

    val toInt = asJavaToIntFunction[String]((value: String) => value.length)
    assertThat(toInt.applyAsInt("forty-two")).isEqualTo(9)
    val toLong = asJavaToLongBiFunction[String, String]((left: String, right: String) => (left + right).length.toLong)
    assertThat(toLong.applyAsLong("scala", "java")).isEqualTo(9L)
    val toDouble = asJavaToDoubleBiFunction[String, String]((left: String, right: String) => left.length.toDouble / right.length.toDouble)
    assertThat(toDouble.applyAsDouble("abcd", "ab")).isEqualTo(2.0d)

    val scalaObjIntConsumer = asScalaFromObjIntConsumer(new java.util.function.ObjIntConsumer[String] {
      override def accept(prefix: String, value: Int): Unit = counter.addAndGet(prefix.length * value)
    })
    scalaObjIntConsumer("xy", 3)
    assertThat(counter.get()).isEqualTo(21)
  }
}
