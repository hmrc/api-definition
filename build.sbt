import _root_.play.core.PlayVersion
import _root_.play.sbt.PlayImport._
import _root_.play.sbt.PlayScala
import uk.gov.hmrc.DefaultBuildSettings._
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._

lazy val appName = "api-definition"

lazy val appDependencies: Seq[ModuleID] = compile ++ test ++ tmpMacWorkaround


lazy val compile = Seq(
  ws,
  "uk.gov.hmrc" %% "bootstrap-play-26" % "1.3.0",
  "uk.gov.hmrc" %% "simple-reactivemongo" % "7.22.0-play-26",
  "uk.gov.hmrc" %% "play-json-union-formatter" % "1.8.0",
  "org.typelevel" %% "cats-core" % "1.1.0"
)


lazy val test = Seq(
  "uk.gov.hmrc" %% "reactivemongo-test" % "4.15.0-play-25" % "test",
  "uk.gov.hmrc" %% "hmrctest" % "3.9.0-play-25" % "test",
  "org.scalaj" %% "scalaj-http" % "2.3.0" % "test",
  "org.scalatest" %% "scalatest" % "3.0.5" % "test, component",
  "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.1" % "test",
  "org.mockito" % "mockito-core" % "2.27.0" % "test",
  "com.typesafe.play" %% "play-test" % PlayVersion.current % "test",
  "com.github.tomakehurst" % "wiremock" % "2.8.0" % "test",
  "de.leanovate.play-mockws" %% "play-mockws" % "2.5.1" % "test"
)
// we need to override the akka version for now as newer versions are not compatible with reactivemongo
lazy val akkaVersion = "2.5.23"
lazy val akkaHttpVersion = "10.0.15"

lazy val overrides = Set(
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-protobuf" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion
)

// Temporary Workaround for intermittent (but frequent) failures of Mongo integration tests when running on a Mac
// See Jira story GG-3666 for further information
def tmpMacWorkaround =
  if (sys.props.get("os.name").exists(_.toLowerCase.contains("mac"))) {
    Seq("org.reactivemongo" % "reactivemongo-shaded-native" % "0.16.1-osx-x86-64" % "runtime,test")
  } else Seq()

lazy val ComponentTest = config("component") extend Test
val testConfig = Seq(ComponentTest, Test)

lazy val plugins: Seq[Plugins] = Seq(PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory)
lazy val playSettings: Seq[Setting[_]] = Seq.empty

lazy val microservice = (project in file("."))
  .enablePlugins(plugins: _*)
  .settings(playSettings: _*)
  .settings(scalaSettings: _*)
  .settings(publishingSettings: _*)
  .settings(defaultSettings(): _*)
  .configs(testConfig: _*)
  .settings(
    name := appName,
    majorVersion := 1,
    targetJvm := "jvm-1.8",
    scalaVersion := "2.11.11",
    scalacOptions += "-Ypartial-unification",
    libraryDependencies ++= appDependencies,
    dependencyOverrides ++= overrides,
    retrieveManaged := true
  )
  .settings(
    unitTestSettings,
    componentTestSettings
  )
  .settings(
    resolvers += Resolver.bintrayRepo("hmrc", "releases"),
    resolvers += Resolver.jcenterRepo)
  .settings(ivyScala := ivyScala.value map {
    _.copy(overrideScalaVersion = true)
  })

lazy val unitTestSettings =
  inConfig(Test)(Defaults.testTasks) ++
    Seq(
      testOptions in Test := Seq(Tests.Filter(onPackageName("unit"))),
      testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-oDT"),
      unmanagedSourceDirectories in Test := Seq((baseDirectory in Test).value / "test"),
      addTestReportOption(Test, "test-reports")
    )

lazy val componentTestSettings =
  inConfig(ComponentTest)(Defaults.testTasks) ++
    Seq(
      testOptions in ComponentTest := Seq(Tests.Filter(onPackageName("component"))),
      testOptions in ComponentTest += Tests.Argument(TestFrameworks.ScalaTest, "-oDT"),
      fork in ComponentTest := false,
      parallelExecution in ComponentTest := false,
      addTestReportOption(ComponentTest, "component-reports")
    )

def onPackageName(rootPackage: String): String => Boolean = {
  testName => testName startsWith rootPackage
}

// Coverage configuration
coverageMinimum := 93
coverageFailOnMinimum := true
coverageExcludedPackages := "<empty>;com.kenshoo.play.metrics.*;prod.*;testOnlyDoNotUseInAppConf.*;app.*;uk.gov.hmrc.BuildInfo"
