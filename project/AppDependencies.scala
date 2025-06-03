import sbt._

object AppDependencies {
  def apply(): Seq[ModuleID] = dependencies ++ testDependencies

  private val bootstrapVersion = "9.12.0"
  private val mongoPlayVersion = "2.6.0"
  val apiDomainVersion = "0.19.1"
  val commonDomainVersion = "0.17.0"

  private lazy val dependencies = Seq(
    "uk.gov.hmrc"       %% "bootstrap-backend-play-30" % bootstrapVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-30"        % mongoPlayVersion,
    "uk.gov.hmrc"       %% "api-platform-api-domain"   % apiDomainVersion
  )

  private lazy val testDependencies = Seq(
    "uk.gov.hmrc"              %% "bootstrap-test-play-30"           % bootstrapVersion,
    "uk.gov.hmrc.mongo"        %% "hmrc-mongo-test-play-30"          % mongoPlayVersion,
    "org.mockito"              %% "mockito-scala-scalatest"          % "1.17.29",
    "uk.gov.hmrc"              %% "api-platform-test-api-domain"     % apiDomainVersion,
    "de.leanovate.play-mockws" %% "play-mockws-3-0"                  % "3.0.2"
  ).map(_ % "test")
}
