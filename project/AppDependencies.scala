import play.core.PlayVersion
import sbt._

object AppDependencies {
  def apply(): Seq[ModuleID] = dependencies ++ testDependencies

  private lazy val dependencies = Seq(
    "uk.gov.hmrc"               %% "bootstrap-play-26"            % "4.0.0",
    "uk.gov.hmrc"               %% "simple-reactivemongo"         % "7.30.0-play-26",
    "uk.gov.hmrc"               %% "play-json-union-formatter"    % "1.12.0-play-26",
    "org.typelevel"             %% "cats-core"                    % "2.1.0",
    "uk.gov.hmrc"               %% "raml-tools"                   % "1.18.0",
    "org.raml"                  %  "raml-parser-2"                % "1.0.13",
    "com.beachape"              %% "enumeratum-play-json"         % "1.6.0"
  )

  private lazy val testDependencies = Seq(
    "uk.gov.hmrc"               %% "reactivemongo-test"           % "4.21.0-play-26",
    "org.scalaj"                %% "scalaj-http"                  % "2.4.1",
    "org.scalatestplus.play"    %% "scalatestplus-play"           % "3.1.3",
    "org.mockito"               %% "mockito-scala-scalatest"      % "1.7.1",
    "com.typesafe.play"         %% "play-test"                    % PlayVersion.current,
    "com.github.tomakehurst"    %  "wiremock-jre8-standalone"     % "2.27.1",
    "de.leanovate.play-mockws"  %% "play-mockws"                  % "2.6.6"
  ).map (m => m % "test, component")
}
