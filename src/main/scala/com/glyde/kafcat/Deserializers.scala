package com.glyde.kafcat

import io.circe.Json
import io.circe.parser.parse
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.common.serialization.StringDeserializer

import scala.jdk.CollectionConverters._

object Deserializers {

  def deserializerFrom(`type`: SupportedType, topic: String, registryUrl: Option[String]) = `type` match {
    case SupportedType.String => stringToJson(topic)
    case SupportedType.Avro   => avroToJson(avroDeserializer(registryUrl.get), topic)
  }

  lazy val stringDeserializer = new StringDeserializer()
  lazy val avroDeserializer: String => KafkaAvroDeserializer = url =>
    new KafkaAvroDeserializer(new CachedSchemaRegistryClient(url, 100), Map(SCHEMA_REGISTRY_URL_CONFIG -> "").asJava)

  def stringToJson(topic: String): Array[Byte] => Json = bytes => {
    val str = stringDeserializer.deserialize(topic, bytes)
    parse(str).getOrElse(Json.fromString(str))
  }

  def avroToJson(deserializer: KafkaAvroDeserializer, topic: String): Array[Byte] => Json = bytes => {
    val gr = deserializer.deserialize(topic, bytes).asInstanceOf[GenericRecord]
    parse(gr.toString).getOrElse(Json.fromString(gr.toString))
  }

}
