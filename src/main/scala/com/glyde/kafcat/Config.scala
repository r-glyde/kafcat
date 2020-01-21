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

  val brokersOpt =
    Opts.option[List[String]]("brokers", help = "comma separated list of brokers")

  val topicNameOpt =
    Opts.option[String]("topic", "name of topic to consume from")

  val registryUrlOpt =
    Opts.option[String Refined Url]("schema-registry-url", help = "url of the schema registry").orNone

  val keyDeserializerOpt =
    Opts
      .option[SupportedType]("key-deserializer", "deserializer for record keys - 'string' (default) or 'avro'")
      .withDefault(SupportedType.String)

  val valueDeserializerOpt =
    Opts
      .option[SupportedType]("value-deserializer", "deserializer for record values - 'string' (default) or 'avro'")
      .withDefault(SupportedType.String)

}
