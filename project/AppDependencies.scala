import sbt._

object AppDependencies {
  def apply(): Seq[ModuleID] = dependencies ++ testDependencies

  private val bootstrapVersion = "8.4.0"
  private val mongoPlayVersion = "1.7.0"
  val apiDomainVersion = "0.16.0"
  val commonDomainVersion = "0.13.0"

  private lazy val dependencies = Seq(
    "uk.gov.hmrc"       %% "bootstrap-backend-play-30" % bootstrapVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-30"        % mongoPlayVersion,
    "uk.gov.hmrc"       %% "api-platform-api-domain"   % apiDomainVersion,
    "uk.gov.hmrc"       %% "raml-tools"                % "1.25.0"
  )

  private lazy val testDependencies = Seq(
    "uk.gov.hmrc"              %% "bootstrap-test-play-30"           % bootstrapVersion,
    "uk.gov.hmrc.mongo"        %% "hmrc-mongo-test-play-30"          % mongoPlayVersion,
    "org.scalaj"               %% "scalaj-http"                      % "2.4.2",
    "org.mockito"              %% "mockito-scala-scalatest"          % "1.17.29",
    "uk.gov.hmrc"              %% "api-platform-test-api-domain"  % apiDomainVersion,
    "de.leanovate.play-mockws" %% "play-mockws-3-0"                  % "3.0.2"
  ).map(_ % "test")
}
