resolvers += "Twitter's Repository" at "https://maven.twttr.com/"
version := "1.0"
scalaVersion := "2.11.7"
libraryDependencies += "com.twitter" %% "finagle-http" % "6.34.0"
libraryDependencies += "org.apache.thrift" % "libthrift" % "0.8.0"
libraryDependencies += "com.twitter" %% "finagle-thrift" % "6.34.0"
libraryDependencies += "com.twitter" %% "util-thrift" % "6.33.0"
libraryDependencies += "com.twitter" %% "scrooge-core" % "4.6.0"
libraryDependencies += "net.lag" %% "kestrel" % "2.4.8-SNAPSHOT"
libraryDependencies ++= Seq(
	"com.fasterxml.jackson.core" % "jackson-core" % "2.6.3",
	"com.fasterxml.jackson.core" % "jackson-databind" % "2.6.3",
	"com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.6.3"
)
libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.6" % "test"

Defaults.itSettings

lazy val root = (project in file(".")).
	aggregate(registration, results).
	dependsOn(registration, results, common).configs(IntegrationTest)

lazy val registration = project.enablePlugins(JavaAppPackaging)
lazy val results = project.dependsOn(registration, common).enablePlugins(JavaAppPackaging)

lazy val common = project

enablePlugins(JavaAppPackaging)