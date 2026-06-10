/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_3 {

import models.QueryStringBindableAnonymous11Value
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.api.mvc.QueryStringBindable

class QueryStringBindableAnonymous11Test {
  @Test
  def bindsJavaQueryStringBindableAndUsesItsJavascriptUnbinder(): Unit = {
    val binder: QueryStringBindable[QueryStringBindableAnonymous11Value] =
      summon[QueryStringBindable[QueryStringBindableAnonymous11Value]]

    val bound: Option[Either[String, QueryStringBindableAnonymous11Value]] =
      binder.bind("item", Map("item" -> Seq("blue", "widget")))

    assertThat(bound.isDefined).isTrue()
    assertThat(bound.get.isRight).isTrue()
    val value: QueryStringBindableAnonymous11Value = bound.get.toOption.get
    assertThat(value.captured).isEqualTo("item=blue,widget")
    assertThat(binder.unbind("item", value)).isEqualTo("item:item=blue,widget")
    assertThat(binder.javascriptUnbind).isEqualTo("function(k,v){return v.captured;}")
  }
}
}

package models {

import java.util.Optional

final class QueryStringBindableAnonymous11Value
    extends play.mvc.QueryStringBindable[QueryStringBindableAnonymous11Value] {
  private var text: String = ""

  override def bind(
      key: String,
      data: java.util.Map[String, Array[String]]
  ): Optional[QueryStringBindableAnonymous11Value] = {
    val values: Array[String] = data.get(key)
    if (values == null) {
      Optional.empty()
    } else {
      val value: QueryStringBindableAnonymous11Value = new QueryStringBindableAnonymous11Value()
      value.text = s"$key=${values.mkString(",")}"
      Optional.of(value)
    }
  }

  override def unbind(key: String): String = s"$key:$text"

  override def javascriptUnbind(): String = "function(k,v){return v.captured;}"

  def captured: String = text
}
}
