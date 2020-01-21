package com.glyde.kafcat

import java.util.UUID

import cats.Show
import cats.effect._
import cats.syntax.set._
import fs2.io.{stdout, stdoutLines}
import fs2.kafka._
import io.circe.Json
import io.circe.literal._
import org.apache.kafka.common.TopicPartition

import scala.collection.immutable.SortedSet
import scala.concurrent.duration._

final case class ConsoleConsumer(brokers: List[String],
                                 topic: String,
                                 keyDeserializer: Array[Byte] => Json,
                                 valueDeserializer: Array[Byte] => Json)(implicit cs: ContextShift[IO], t: Timer[IO]) {

  private val settings = ConsumerSettings[IO, Array[Byte], Array[Byte]]
    .withBootstrapServers(brokers.mkString(","))
    .withGroupId(UUID.randomUUID().toString)
    .withAutoOffsetReset(AutoOffsetReset.Earliest)

  private val consumer = consumerStream(settings)

  private implicit val partitionOrder: Ordering[TopicPartition] = (x, y) =>
    Ordering.Int.compare(x.partition, y.partition)

  private implicit val showJson: Show[Json] = _.noSpaces + "\n"

  def run(blocker: Blocker): fs2.Stream[IO, Unit] =
    consumer.evalMap { c =>
      for {
        partitions      <- c.partitionsFor(topic)
        topicPartitions = partitions.map(p => new TopicPartition(topic, p.partition)).toSet
        startOffsets    <- c.beginningOffsets(topicPartitions)
        endOffsets      <- c.endOffsets(topicPartitions)
      } yield (startOffsets, endOffsets)
    }.flatMap {
      case (startOffsets, _endOffsets) =>
        val endOffsets = _endOffsets.map { case (tp, o) => tp.partition -> o }
        val full = startOffsets.filter { case (tp, start) => start < endOffsets(tp.partition) }
        SortedSet.from(full.keySet).toNes.fold[fs2.Stream[IO, Unit]](fs2.Stream.empty) { partitions =>
          consumer
            .evalTap(_.assign(partitions))
            .flatMap { c =>
              c.partitionedStream.map { ps =>
                ps.takeThrough { cr =>
                  (cr.record.offset + 1) < endOffsets(cr.record.partition)
                }.map { cr =>
                  json"""
                {
                  "key": ${cr.record.serializedKeySize.fold(Json.Null)(_ => keyDeserializer(cr.record.key))},
                  "value": ${cr.record.serializedValueSize.fold(Json.Null)(_ => valueDeserializer(cr.record.value))},
                  "topic": ${cr.record.topic},
                  "partition": ${cr.record.partition},
                  "offset": ${cr.record.offset},
                  "timestamp": ${cr.record.timestamp.createTime.getOrElse(-1L)}
                }
              """
                }
              }
            }
            .take(full.size)
            .parJoinUnbounded
            .through(stdoutLines(blocker))
        }
    }

}
