/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_actor_2_13

import java.net.URL
import java.net.URLClassLoader
import java.util.Base64

import org.assertj.core.api.Assertions.assertThat
import org.graalvm.internal.tck.NativeImageSupport
import org.junit.jupiter.api.Test

class ByteBufferCleanerInnerJava8CleanerTest {
  @Test
  def java8CleanerInvokesDirectBufferCleanerAndCleanMethods(): Unit = {
    try {
      val loader: ClassLoader = new ByteBufferCleanerInnerJava8CleanerTest.Java8CleanerClassLoader(getClass.getClassLoader)
      val probeClass: Class[_] = loader.loadClass("org.apache.pekko.io.ByteBufferCleanerProbe")

      probeClass.getMethod("cleanDirectBuffer").invoke(null)

      val directBufferClass: Class[_] = loader.loadClass("sun.nio.ch.DirectBuffer")
      val cleanerClass: Class[_] = loader.loadClass("sun.misc.Cleaner")

      assertThat(directBufferClass.getField("cleanerCount").getInt(null)).isGreaterThanOrEqualTo(1)
      assertThat(cleanerClass.getField("cleanCount").getInt(null)).isGreaterThanOrEqualTo(1)
    } catch {
      case error: Error =>
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
          throw error
        }
    }
  }
}

object ByteBufferCleanerInnerJava8CleanerTest {
  private val cleanerClassName: String = "sun.misc.Cleaner"
  private val directBufferClassName: String = "sun.nio.ch.DirectBuffer"
  private val probeClassName: String = "org.apache.pekko.io.ByteBufferCleanerProbe"
  private val byteBufferCleanerClassPrefix: String = "org.apache.pekko.io.ByteBufferCleaner"

  private val generatedClasses: Map[String, Array[Byte]] = Map(
    cleanerClassName -> decode(
      "yv66vgAAADQAEgoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQADKClWCQAIAAkHAAoMAAsADAEAEHN1bi9taXNjL0NsZWFuZXIBAApjbGVhbkNvdW50AQABSQEABENvZGUBAA9MaW5lTnVtYmVyVGFibGUBAAVjbGVhbgEAClNvdXJjZUZpbGUBAAxDbGVhbmVyLmphdmEAMQAIAAIAAAABAAkACwAMAAAAAgABAAUABgABAA0AAAAdAAEAAQAAAAUqtwABsQAAAAEADgAAAAYAAQAAAAIAAQAPAAYAAQANAAAAIQACAAEAAAAJsgAHBGCzAAexAAAAAQAOAAAABgABAAAABAABABAAAAACABE="),
    directBufferClassName -> decode(
      "yv66vgAAADQAFgoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQADKClWCQAIAAkHAAoMAAsADAEAF3N1bi9uaW8vY2gvRGlyZWN0QnVmZmVyAQAMY2xlYW5lckNvdW50AQABSQcADgEAEHN1bi9taXNjL0NsZWFuZXIKAA0AAwEABENvZGUBAA9MaW5lTnVtYmVyVGFibGUBAAdjbGVhbmVyAQAUKClMc3VuL21pc2MvQ2xlYW5lcjsBAApTb3VyY2VGaWxlAQARRGlyZWN0QnVmZmVyLmphdmEAMQAIAAIAAAABAAkACwAMAAAAAgABAAUABgABABAAAAAdAAEAAQAAAAUqtwABsQAAAAEAEQAAAAYAAQAAAAIACQASABMAAQAQAAAALAACAAAAAAAQsgAHBGCzAAe7AA1ZtwAPsAAAAAEAEQAAAAoAAgAAAAUACAAGAAEAFAAAAAIAFQ=="),
    probeClassName -> decode(
      "yv66vgAAADQAGgoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQADKClWCgAIAAkHAAoMAAsADAEAE2phdmEvbmlvL0J5dGVCdWZmZXIBAA5hbGxvY2F0ZURpcmVjdAEAGChJKUxqYXZhL25pby9CeXRlQnVmZmVyOwoADgAPBwAQDAARABIBACVvcmcvYXBhY2hlL3Bla2tvL2lvL0J5dGVCdWZmZXJDbGVhbmVyAQAFY2xlYW4BABgoTGphdmEvbmlvL0J5dGVCdWZmZXI7KVYHABQBACpvcmcvYXBhY2hlL3Bla2tvL2lvL0J5dGVCdWZmZXJDbGVhbmVyUHJvYmUBAARDb2RlAQAPTGluZU51bWJlclRhYmxlAQARY2xlYW5EaXJlY3RCdWZmZXIBAApTb3VyY2VGaWxlAQAbQnl0ZUJ1ZmZlckNsZWFuZXJQcm9iZS5qYXZhADEAEwACAAAAAAACAAIABQAGAAEAFQAAAB0AAQABAAAABSq3AAGxAAAAAQAWAAAABgABAAAABAAJABcABgABABUAAAAlAAEAAAAAAAkQCLgAB7gADbEAAAABABYAAAAKAAIAAAAGAAgABwABABgAAAACABk=")
  )

  private def decode(bytes: String): Array[Byte] = {
    Base64.getDecoder.decode(bytes)
  }

  final class Java8CleanerClassLoader(parent: ClassLoader) extends URLClassLoader(Array.empty[URL], parent) {
    private var pekkoActorJarAdded: Boolean = false

    override protected def loadClass(name: String, resolve: Boolean): Class[_] = {
      if (loadsJava8CleanerScenarioClass(name)) {
        val loadedClass: Class[_] = findLoadedClass(name) match {
          case null => findClass(name)
          case existingClass => existingClass
        }
        if (resolve) {
          resolveClass(loadedClass)
        }
        loadedClass
      } else {
        super.loadClass(name, resolve)
      }
    }

    override protected def findClass(name: String): Class[_] = {
      generatedClasses.get(name) match {
        case Some(classBytes) =>
          defineClass(name, classBytes, 0, classBytes.length)
        case None if name.startsWith(byteBufferCleanerClassPrefix) =>
          ensurePekkoActorJarAdded()
          super.findClass(name)
        case None =>
          super.findClass(name)
      }
    }

    private def loadsJava8CleanerScenarioClass(name: String): Boolean = {
      generatedClasses.contains(name) || name.startsWith(byteBufferCleanerClassPrefix)
    }

    private def ensurePekkoActorJarAdded(): Unit = {
      if (!pekkoActorJarAdded) {
        addURL(pekkoActorJarUrl())
        pekkoActorJarAdded = true
      }
    }
  }

  private def pekkoActorJarUrl(): URL = {
    val resourceName: String = classResourceName("org.apache.pekko.io.ByteBufferCleaner")
    val resource: URL = getClass.getClassLoader.getResource(resourceName)
    if (resource == null) {
      throw new IllegalStateException(s"Unable to locate $resourceName")
    }

    val externalForm: String = resource.toExternalForm
    val separatorIndex: Int = externalForm.indexOf("!/")
    if (!externalForm.startsWith("jar:") || separatorIndex < 0) {
      throw new IllegalStateException(s"Expected $resourceName to come from a jar URL, but found $externalForm")
    }
    new URL(externalForm.substring("jar:".length, separatorIndex))
  }

  private def classResourceName(name: String): String = {
    name.replace('.', '/') + ".class"
  }
}
