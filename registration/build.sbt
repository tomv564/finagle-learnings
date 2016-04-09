resolvers += "Twitter's Repository" at "https://maven.twttr.com/"
version := "1.0"
scalaVersion := "2.11.7"

libraryDependencies += "org.apache.thrift" % "libthrift" % "0.8.0"
libraryDependencies += "com.twitter" %% "finagle-thrift" % "6.34.0"
libraryDependencies += "com.twitter" %% "scrooge-core" % "4.6.0"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.6" % "test"
