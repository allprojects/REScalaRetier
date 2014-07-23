organization := "de.tuda.stg"

name := "rescala"

version := "0.0.0"

scalaVersion := "2.11.1"

scalaSource in Compile <<= baseDirectory {(base) => new File(base, "REScala/src")}

scalaSource in Test <<= baseDirectory {(base) => new File(base, "REScalaTests/test")}

scalacOptions ++= List(
  "-deprecation",
  "-encoding", "UTF-8",
  "-unchecked",
  "-feature",
  "-target:jvm-1.6",
  "-language:implicitConversions",
  "-language:reflectiveCalls",
  "-Xlint",
  "-language:postfixOps"
)

libraryDependencies ++= Seq(
  "org.mockito" % "mockito-all" % "1.9.5" % "test",
  "org.scalatest" %% "scalatest" % "2.2.0" % "test",
  "com.novocode" % "junit-interface" % "0.10" % "test"
)

libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-compiler" % _)

parallelExecution in Test := false
