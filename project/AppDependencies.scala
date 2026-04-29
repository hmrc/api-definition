import sbt._

object AppDependencies {
  def apply(): Seq[ModuleID] = dependencies ++ testDependencies

  private val bootstrapVersion    = "10.7.0"
  private val mongoPlayVersion    = "2.12.0"
  private val apiDomainVersion    = "1.4.0"
  private val mockitoScalaVersion = "2.0.0"

  private lazy val dependencies = Seq(
    "uk.gov.hmrc"       %% "bootstrap-backend-play-30" % bootstrapVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-30"        % mongoPlayVersion,
    "uk.gov.hmrc"       %% "api-platform-api-domain"   % apiDomainVersion
  )

  private lazy val testDependencies = Seq(
    "uk.gov.hmrc"              %% "bootstrap-test-play-30"           % bootstrapVersion,
    "uk.gov.hmrc.mongo"        %% "hmrc-mongo-test-play-30"          % mongoPlayVersion,
    "org.mockito"              %% "mockito-scala-scalatest"          % mockitoScalaVersion,
    "uk.gov.hmrc"              %% "api-platform-api-domain-fixtures" % apiDomainVersion,
    "de.leanovate.play-mockws" %% "play-mockws-3-0"                  % "3.0.2"
  ).map(_ % "test")
}
