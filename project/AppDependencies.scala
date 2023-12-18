import play.core.PlayVersion
import sbt._

object AppDependencies {
  def apply(): Seq[ModuleID] = dependencies ++ testDependencies

  private val bootstrapVersion = "7.15.0"
  private val mongoPlayVersion = "0.74.0"
  val apiDomainVersion = "0.11.0"
  val commonDomainVersion = "0.10.0"

  private lazy val dependencies = Seq(
    "uk.gov.hmrc"       %% "bootstrap-backend-play-28" % bootstrapVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"        % mongoPlayVersion,
    "uk.gov.hmrc"       %% "api-platform-api-domain"   % apiDomainVersion,
    "uk.gov.hmrc"       %% "raml-tools"                % "1.23.0"
  )

  private lazy val testDependencies = Seq(
    "uk.gov.hmrc"                 %% "bootstrap-test-play-28"            % bootstrapVersion,
    "uk.gov.hmrc.mongo"           %% "hmrc-mongo-test-play-28"           % mongoPlayVersion,
    "org.scalaj"                  %% "scalaj-http"                       % "2.4.2",
    "org.mockito"                 %% "mockito-scala-scalatest"           % "1.17.22",
    "com.typesafe.play"           %% "play-test"                         % PlayVersion.current,
    "de.leanovate.play-mockws"    %% "play-mockws"                       % "2.8.1",
    "uk.gov.hmrc"                 %% "api-platform-test-common-domain"   % commonDomainVersion,
  ).map(_ % "test, component")
}
