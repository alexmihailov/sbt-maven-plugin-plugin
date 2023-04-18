ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "io.github.alexmihailov"
ThisBuild / description := "Sbt plugin that allows to generate a plugin.xml descriptor file and run maven tests in SBT."
ThisBuild / licenses := List(
  "Apache 2" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt")
)

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/alexmihailov/sbt-maven-plugin-plugin"),
    "scm:git@github.com:alexmihailov/sbt-maven-plugin-plugin"
  )
)
ThisBuild / developers := List(
  Developer(
    id = "alexmihailov",
    name = "Alex Mihailov",
    email = "av.mihailov.dev@gmail.com",
    url = url("https://github.com/alexmihailov")
  )
)

// Remove all additional repository other than Maven Central from POM
ThisBuild / pomIncludeRepository := { _ => false }
ThisBuild / publishTo := {
  val nexus = "https://s01.oss.sonatype.org/"
  if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
  else Some("releases" at nexus + "service/local/staging/deploy/maven2")
}
ThisBuild / publishMavenStyle := true

lazy val root = project
  .in(file("."))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "sbt-maven-plugin-plugin",
    libraryDependencies += "org.apache.maven.plugins" % "maven-plugin-plugin" % Dependencies.Maven,
    pluginCrossBuild / sbtVersion := {
      scalaBinaryVersion.value match {
        case "2.12" => "1.2.8" // set minimum sbt version
      }
    },
    scriptedLaunchOpts := {
      scriptedLaunchOpts.value ++
        Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
    },
    scriptedBufferLog := false
  )
