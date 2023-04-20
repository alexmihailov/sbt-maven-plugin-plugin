# Sbt Maven Plugin Plugin

This plugin is similar of the [Maven Plugin Plugin](https://maven.apache.org/plugin-tools/maven-plugin-plugin/).

## Features

### Generate maven plugin descriptor
Maven plugin requires `plugin.xml` descriptor file. More details can be found in the [documentation](https://maven.apache.org/ref/3.9.1/maven-plugin-api/plugin.html).
`Sbt Maven Plugin Plugin` allows to generate a `plugin.xml` in SBT.

To generate the descriptor file, need to execute the `mavenGeneratePluginXml` task.
This task depends on the `compile` task and scans class files for maven plugin annotations.
Also, this task is included in the list of tasks for generating resources (`resourceGenerators`), so it will be called
when building the jar and the `plugin.xml` file will automatically be added to the jar.

#### Required settings keys
- `mavenPluginGoalPrefix` - specifying a plugin's prefix, [documentation](https://maven.apache.org/guides/introduction/introduction-to-plugin-prefix-mapping.html)

#### Optional settings keys
- `mavenPluginEncoding` - specifying a plugin's encoding, default value `UTF-8`
- `mavenPluginSkipErrorNoDescriptorsFound` - specifying a plugin's skip error when descriptors not found, default value `false`

### Run maven tests
The plugin allows you to run maven tests - a full launch of the maven build.

For this it is necessary:
- place the maven project in the directory `/src/maven-test`
- in the directory with the maven project, create a test file (`/src/maven-test/{project}/test`) in which you specify the maven phases
- call the `mavenTest` task, if there are several projects in the `maven-test` directory, they will be called one by one, to indicate the execution of a specific project, you must specify the directory name when calling the task - `mavenTest {directory}`

#### Required tasks keys
- `mavenClasspath` - specifying a plugin's maven launcher classpath.
This property is only checked when executing the mavenTest task.
To get the maven launch classpath, you can create a subproject with dependency `apache-maven` and extract `externalDependencyClasspath` from it.

#### Optional settings keys
- `mavenTestArgs` - specifying a maven test arguments, default value:
```
      "-Xmx768m",
      "-XX:MaxMetaspaceSize=384m",
      "-Dhttps.protocols=TLSv1,TLSv1.1,TLSv1.2",
      "-Dorg.slf4j.simpleLogger.showLogName=false",
      "-Dorg.slf4j.simpleLogger.showThreadName=false"
```
Through this property, you can pass the version of the plugin being created to be used in `pom.xml`.
- `mavenPrintTestArgs` - enable printing arguments for maven tests, default value `false`

## Examples

Examples can be seen in [simple](./src/sbt-test/sbt-maven/simple).