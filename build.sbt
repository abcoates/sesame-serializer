name := "sesame-serializer"

version := "0.1"

scalaVersion := "2.11.5"

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

libraryDependencies += "org.scalatest" % "scalatest_2.11" % "2.2.1" % "test"

libraryDependencies += "org.openrdf.sesame" % "sesame-runtime" % "2.7.14"

libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.10"

libraryDependencies += "org.slf4j" % "slf4j-log4j12" % "1.7.10"

libraryDependencies += "commons-cli" % "commons-cli" % "1.2"

