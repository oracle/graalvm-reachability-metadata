/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_3 {

import models.PathBindableAnonymous12Value
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.api.mvc.PathBindable

class PathBindableAnonymous12Test {
  @Test
  def bindsJavaPathBindableAndUsesItsJavascriptUnbinder(): Unit = {
    val binder: PathBindable[PathBindableAnonymous12Value] = summon[PathBindable[PathBindableAnonymous12Value]]

    val bound: Either[String, PathBindableAnonymous12Value] = binder.bind("item", "blue-widget")

    assertThat(bound.isRight).isTrue()
    val value: PathBindableAnonymous12Value = bound.toOption.get
    assertThat(value.captured).isEqualTo("item=blue-widget")
    assertThat(binder.unbind("item", value)).isEqualTo("item:item=blue-widget")
    assertThat(binder.javascriptUnbind).isEqualTo("function(k,v){return v.captured;}")
  }
}
}

package models {

final class PathBindableAnonymous12Value extends play.mvc.PathBindable[PathBindableAnonymous12Value] {
  private var text: String = ""

  override def bind(key: String, txt: String): PathBindableAnonymous12Value = {
    val value: PathBindableAnonymous12Value = new PathBindableAnonymous12Value()
    value.text = s"$key=$txt"
    value
  }

  override def unbind(key: String): String = s"$key:$text"

  override def javascriptUnbind(): String = "function(k,v){return v.captured;}"

  def captured: String = text
}
}
