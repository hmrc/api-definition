import play.core.PlayVersion
import sbt._

object AppDependencies {
  def apply(): Seq[ModuleID] = dependencies ++ testDependencies

  private val bootstrapVersion = "7.12.0"
  private val mongoPlayVersion = "0.74.0"

  private lazy val dependencies = Seq(
    "uk.gov.hmrc"       %% "bootstrap-backend-play-28" % bootstrapVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"        % mongoPlayVersion,
    "uk.gov.hmrc"       %% "play-json-union-formatter" % "1.17.0-play-28",
    "uk.gov.hmrc"       %% "raml-tools"                % "1.22.0",
    "org.typelevel"     %% "cats-core"                 % "2.7.0",
    "com.beachape"      %% "enumeratum-play-json"      % "1.7.0"
  )

  private lazy val testDependencies = Seq(
    "uk.gov.hmrc"              %% "bootstrap-test-play-28"  % bootstrapVersion,
    "uk.gov.hmrc.mongo"        %% "hmrc-mongo-test-play-28" % mongoPlayVersion,
    "org.scalaj"               %% "scalaj-http"             % "2.4.2",
    "org.mockito"              %% "mockito-scala-scalatest" % "1.17.7",
    "com.typesafe.play"        %% "play-test"               % PlayVersion.current,
    "de.leanovate.play-mockws" %% "play-mockws"             % "2.8.1"
  ).map(_ % "test, component")
}
