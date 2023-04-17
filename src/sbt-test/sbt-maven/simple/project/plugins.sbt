sys.props.get("plugin.version") match {
  case Some(x) => addSbtPlugin("com.alexmihailov" % "sbt-maven-plugin-plugin" % x)
  case _ => sys.error("""|The system property 'plugin.version' is not defined.
                         |Specify this property using the scriptedLaunchOpts -D.""".stripMargin)
}

//addSbtPlugin("com.alexmihailov" % "sbt-maven-plugin-plugin" % "0.1.0-SNAPSHOT")
