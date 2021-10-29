import play.core.PlayVersion
import sbt._

object AppDependencies {
  def apply(): Seq[ModuleID] = dependencies ++ testDependencies

  private lazy val dependencies = Seq(
    "uk.gov.hmrc"               %% "bootstrap-backend-play-28"    % "5.16.0",
    "uk.gov.hmrc"               %% "simple-reactivemongo"         % "8.0.0-play-28",
    "uk.gov.hmrc"               %% "play-json-union-formatter"    % "1.15.0-play-28",
    "uk.gov.hmrc"               %% "raml-tools"                   % "1.20.0",
    "org.typelevel"             %% "cats-core"                    % "2.6.1",
    "com.beachape"              %% "enumeratum-play-json"         % "1.7.0"
  )

  private lazy val testDependencies = Seq(
    "uk.gov.hmrc"               %% "bootstrap-test-play-28"       % "5.16.0",
    "uk.gov.hmrc"               %% "reactivemongo-test"           % "5.0.0-play-28",
    "org.scalaj"                %% "scalaj-http"                  % "2.4.2",
    "org.mockito"               %% "mockito-scala-scalatest"      % "1.16.46",
    "com.typesafe.play"         %% "play-test"                    % PlayVersion.current,
    "com.github.tomakehurst"    %  "wiremock-jre8-standalone"     % "2.31.0",
    "de.leanovate.play-mockws"  %% "play-mockws"                  % "2.8.1"
  ).map (m => m % "test, component")
}
