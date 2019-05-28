name := "K8Bot"

version := "0.1"

scalaVersion := "2.12.8"

libraryDependencies += "com.github.slack-scala-client" %% "slack-scala-client" % "0.2.6"

libraryDependencies += "io.skuber" %% "skuber" % "2.2.0"

libraryDependencies += "com.typesafe.akka" %% "akka-http"   % "10.1.8"
libraryDependencies += "com.typesafe.akka" %% "akka-stream" % "2.5.19"

libraryDependencies += "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml" % "2.9.9"

libraryDependencies += "io.spray" %%  "spray-json" % "1.3.5"

libraryDependencies += "com.spotify" % "docker-client" % "8.16.0"
