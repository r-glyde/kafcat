import com.typesafe.sbt.packager.docker.Cmd

name := "kafcat"
version := "0.1.0"
scalaVersion := "2.13.1"

enablePlugins(JavaAppPackaging, DockerPlugin)

dockerBaseImage := "openjdk:8u201-alpine"
dockerRepository := Some("glyderj")
dockerLabels := Map("maintainer" -> "r-glyde")
dockerUpdateLatest := true
packageName in Docker := name.value
dockerCommands ++= Seq(
  Cmd("USER", "root"),
  Cmd("RUN", "apk update && apk add bash")
)


resolvers += ("confluent-release" at "http://packages.confluent.io/maven/").withAllowInsecureProtocol(true)

libraryDependencies ++= Seq(
  "com.github.fd4s" %% "fs2-kafka"            % "1.0.0-RC1",
  "org.typelevel"   %% "cats-core"            % "2.1.0",
  "org.typelevel"   %% "cats-effect"          % "2.0.0",
  "co.fs2"          %% "fs2-core"             % "2.1.0",
  "co.fs2"          %% "fs2-io"               % "2.1.0",
  "io.circe"        %% "circe-core"           % circeVersion,
  "io.circe"        %% "circe-generic"        % circeVersion,
  "io.circe"        %% "circe-parser"         % circeVersion,
  "io.circe"        %% "circe-literal"        % circeVersion,
  "io.confluent"    % "kafka-avro-serializer" % "5.4.0",
  "com.monovore"    %% "decline"              % declineVersion,
  "com.monovore"    %% "decline-effect"       % declineVersion,
  "com.monovore"    %% "decline-refined"      % declineVersion,
  "eu.timepit"      %% "refined"              % "0.9.10",
  "org.slf4j"       % "slf4j-nop"             % "1.7.30"
)

lazy val circeVersion   = "0.12.3"
lazy val declineVersion = "1.0.0"
