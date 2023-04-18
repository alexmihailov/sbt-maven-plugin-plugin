ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "com.alexmihailov"

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
