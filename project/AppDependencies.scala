import play.core.PlayVersion
import sbt._

object AppDependencies {
  def apply(): Seq[ModuleID] = dependencies ++ testDependencies

  private val bootstrapVersion = "7.15.0"
  private val mongoPlayVersion = "1.7.0"
  val apiDomainVersion = "0.11.0"
  val commonDomainVersion = "0.10.0"

  private lazy val dependencies = Seq(
    "uk.gov.hmrc"       %% "bootstrap-backend-play-28" % bootstrapVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"        % mongoPlayVersion,
    "uk.gov.hmrc"       %% "api-platform-api-domain"   % apiDomainVersion,
    "uk.gov.hmrc"       %% "raml-tools"                % "1.23.0"
  )

  private lazy val testDependencies = Seq(
    "uk.gov.hmrc"              %% "bootstrap-test-play-28"  % bootstrapVersion,
    "uk.gov.hmrc.mongo"        %% "hmrc-mongo-test-play-28" % mongoPlayVersion,
    "org.scalaj"               %% "scalaj-http"             % "2.4.2",
    "org.mockito"              %% "mockito-scala-scalatest" % "1.17.29",
    "com.typesafe.play"        %% "play-test"               % PlayVersion.current,
    "org.scalatest"            %% "scalatest"               % "3.2.17",
    "com.vladsch.flexmark"     %  "flexmark-all"            % "0.62.2",
    "uk.gov.hmrc"              %% "api-platform-test-common-domain"   % commonDomainVersion,
    "de.leanovate.play-mockws" %% "play-mockws"             % "2.8.1"
  ).map(_ % "test, component, it")
}
