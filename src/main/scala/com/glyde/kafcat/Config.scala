package com.glyde.kafcat

import cats.data.Validated.{invalidNel, valid}
import cats.data.ValidatedNel
import com.monovore.decline._
import com.monovore.decline.refined._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.string.Url

object Config {

  implicit val readStrings: Argument[List[String]] = new Argument[List[String]] {
    override def read(input: String): ValidatedNel[String, List[String]] = valid(input.split(',').toList)
    override def defaultMetavar: String                                  = "string,string..."
  }

  implicit val readDeserializer: Argument[SupportedType] = new Argument[SupportedType] {
    override def read(input: String): ValidatedNel[String, SupportedType] = input.toLowerCase match {
      case "string" => valid(SupportedType.String)
      case "avro"   => valid(SupportedType.Avro)
      case _        => invalidNel(s"Invalid deserializer, '$input': should be 'string' or 'avro'")
    }
    override def defaultMetavar: String = "string"
  }

  val offsetHelp =
    "'earliest', 'latest' or long representing the offset from the beginning (if positive) or end (if negative)"

  implicit val readOffset: Argument[Offset] = new Argument[Offset] {
    override def read(input: String): ValidatedNel[String, Offset] = input.toLowerCase match {
      case "earliest" => valid(Offset.Earliest)
      case "latest"   => valid(Offset.Latest)
      case value =>
        value.toLongOption.fold[ValidatedNel[String, Offset]](invalidNel(s"Invalid offset, '$input': $offsetHelp")) {
          v =>
            if (v < 0) valid(Offset.FromEnd(v * -1))
            else if (v > 0) valid(Offset.FromBeginning(v))
            else valid(Offset.Earliest)
        }
    }
    override def defaultMetavar: String = "string"
  }

  val brokersOpt =
    Opts.option[List[String]]("brokers", short = "b", help = "comma separated list of brokers")

  val topicNameOpt =
    Opts.option[String]("topic", short = "t", help = "name of topic to consume from")

  val offsetOpt =
    Opts
      .option[Offset](
        "offset",
        short = "o",
        help = "offset to read from - " + offsetHelp
      )
      .withDefault(Offset.Earliest)

  val registryUrlOpt =
    Opts.option[String Refined Url]("schema-registry-url", short = "r", help = "url of the schema registry").orNone

  val keyDeserializerOpt =
    Opts
      .option[SupportedType]("key-deserializer",
                             short = "k",
                             help = "deserializer for record keys - 'string' (default) or 'avro'")
      .withDefault(SupportedType.String)

  val valueDeserializerOpt =
    Opts
      .option[SupportedType]("value-deserializer",
                             short = "v",
                             help = "deserializer for record values - 'string' (default) or 'avro'")
      .withDefault(SupportedType.String)

}
