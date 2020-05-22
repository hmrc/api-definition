import _root_.play.core.PlayVersion
import _root_.play.sbt.PlayImport._
import _root_.play.sbt.PlayScala
import uk.gov.hmrc.DefaultBuildSettings._
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._

lazy val appName = "api-definition"

lazy val appDependencies: Seq[ModuleID] = compile ++ test ++ tmpMacWorkaround


lazy val compile = Seq(
  ws,
  "uk.gov.hmrc" %% "bootstrap-play-26" % "1.4.0",
  "uk.gov.hmrc" %% "simple-reactivemongo" % "7.23.0-play-26",
  "uk.gov.hmrc" %% "play-json-union-formatter" % "1.11.0",
  "org.typelevel" %% "cats-core" % "1.1.0",
)

lazy val test = Seq(
  "uk.gov.hmrc" %% "reactivemongo-test" % "4.16.0-play-26" % "test",
  "uk.gov.hmrc" %% "hmrctest" % "3.9.0-play-26" % "test",
  "org.scalaj" %% "scalaj-http" % "2.4.1" % "test",
  "org.scalatest" %% "scalatest" % "3.0.5" % "test, component",
  "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % "test",
  "org.mockito" % "mockito-core" % "2.13.0" % "test",
  "com.typesafe.play" %% "play-test" % PlayVersion.current % "test",
  "com.github.tomakehurst" % "wiremock" % "2.21.0" % "test",
  "de.leanovate.play-mockws" %% "play-mockws" % "2.6.6" % "test"
)
// we need to override the akka version for now as newer versions are not compatible with reactivemongo
lazy val akkaVersion = "2.5.23"
lazy val akkaHttpVersion = "10.0.15"
val jettyVersion = "9.4.26.v20200117"

lazy val overrides = Seq(
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-protobuf" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion,
  "org.eclipse.jetty"           % "jetty-server"       % jettyVersion,
  "org.eclipse.jetty"           % "jetty-servlet"      % jettyVersion,
  "org.eclipse.jetty"           % "jetty-security"     % jettyVersion,
  "org.eclipse.jetty"           % "jetty-servlets"     % jettyVersion,
  "org.eclipse.jetty"           % "jetty-continuation" % jettyVersion,
  "org.eclipse.jetty"           % "jetty-webapp"       % jettyVersion,
  "org.eclipse.jetty"           % "jetty-xml"          % jettyVersion,
  "org.eclipse.jetty"           % "jetty-client"       % jettyVersion,
  "org.eclipse.jetty"           % "jetty-http"         % jettyVersion,
  "org.eclipse.jetty"           % "jetty-io"           % jettyVersion,
  "org.eclipse.jetty"           % "jetty-util"         % jettyVersion,
  "org.eclipse.jetty.websocket" % "websocket-api"      % jettyVersion,
  "org.eclipse.jetty.websocket" % "websocket-common"   % jettyVersion,
  "org.eclipse.jetty.websocket" % "websocket-client"   % jettyVersion
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
  .disablePlugins(JUnitXmlReportPlugin)
  .settings(playSettings: _*)
  .settings(scalaSettings: _*)
  .settings(publishingSettings: _*)
  .settings(defaultSettings(): _*)
  .configs(testConfig: _*)
  .settings(
    name := appName,
    majorVersion := 1,
    targetJvm := "jvm-1.8",
    scalaVersion := "2.12.10",
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
coverageMinimum := 91
coverageFailOnMinimum := true
coverageExcludedPackages := "<empty>;com.kenshoo.play.metrics.*;prod.*;testOnlyDoNotUseInAppConf.*;app.*;uk.gov.hmrc.BuildInfo"
