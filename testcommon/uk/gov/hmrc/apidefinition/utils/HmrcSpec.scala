package uk.gov.hmrc.apidefinition.utils

import org.mockito.{MockitoSugar, ArgumentMatchersSugar}
import org.scalatest.{Matchers, OptionValues, WordSpec}
import org.scalatestplus.play.WsScalaTestClient
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}

abstract class HmrcSpec extends WordSpec with Matchers with OptionValues with WsScalaTestClient with MockitoSugar with ArgumentMatchersSugar

abstract class AsyncHmrcSpec
  extends HmrcSpec with DefaultAwaitTimeout with FutureAwaits {
}
