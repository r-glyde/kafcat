package com.glyde.kafcat

import cats.effect._
import cats.implicits._
import com.monovore.decline._
import com.monovore.decline.effect._

object Main
    extends CommandIOApp(
      name = "kafcat",
      header = "Consume (and maybe more) from kafka topics",
      version = "0.1.0"
    ) {

  import Config._
  import Deserializers._

  override def main: Opts[IO[ExitCode]] =
    (brokersOpt, topicNameOpt, registryUrlOpt, keyDeserializerOpt, valueDeserializerOpt).mapN {
      case (_, _, None, SupportedType.Avro, _) | (_, _, None, _, SupportedType.Avro) =>
        println("Schema registry URL required to use avro deserializer")
        IO(ExitCode.Error)
      case (brokers, topic, maybeRegistryUrl, kD, vD) =>
        val maybeRegistryString = maybeRegistryUrl.map(_.value)
        val consumer = ConsoleConsumer(brokers,
                                       topic,
                                       deserializerFrom(kD, topic, maybeRegistryString),
                                       deserializerFrom(vD, topic, maybeRegistryString))
        Blocker[IO].use(consumer.run(_).compile.drain.as(ExitCode.Success))
    }

}
