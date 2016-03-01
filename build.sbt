name := "finagle-test"
version := "1.0"
scalaVersion := "2.11.7"
libraryDependencies += "com.twitter" %% "finagle-http" % "6.33.0"
libraryDependencies ++= Seq(
	"com.fasterxml.jackson.core" % "jackson-databind" % "2.6.3",
	"com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.6.3"
)
libraryDependencies += "org.scalatest" % "scalatest_2.11" % "2.2.6" % "test"


