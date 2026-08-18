/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_slick.slick_2_13

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import slick.util.ClassLoaderUtil

class ClassLoaderUtilAnonymous1Test {
  @Test
  def defaultClassLoaderFallsBackWhenThreadContextClassLoaderCannotFindClass(): Unit = {
    val originalClassLoader: ClassLoader = Thread.currentThread().getContextClassLoader
    val rejectingClassLoader: ClassLoader = new ClassLoader(null) {
      override def loadClass(name: String): Class[?] =
        throw new ClassNotFoundException(name)
    }

    Thread.currentThread().setContextClassLoader(rejectingClassLoader)
    try {
      val loadedClass: Class[?] = ClassLoaderUtil.defaultClassLoader.loadClass(ClassLoaderUtil.getClass.getName)

      assertThat(loadedClass).isSameAs(ClassLoaderUtil.getClass)
    } finally {
      Thread.currentThread().setContextClassLoader(originalClassLoader)
    }
  }
}
