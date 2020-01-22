package com.glyde.kafcat

import java.util.UUID

import cats.Show
import cats.effect._
import cats.implicits._
import fs2.io.stdoutLines
import fs2.kafka._
import io.circe.Json
import io.circe.literal._
import org.apache.kafka.common.TopicPartition

import scala.collection.immutable.SortedSet

final case class ConsoleConsumer(brokers: List[String],
                                 topic: String,
                                 offset: Offset,
                                 keyDeserializer: Array[Byte] => Json,
                                 valueDeserializer: Array[Byte] => Json)(implicit cs: ContextShift[IO], t: Timer[IO]) {

  private val settings = ConsumerSettings[IO, Array[Byte], Array[Byte]]
    .withBootstrapServers(brokers.mkString(","))
    .withGroupId(UUID.randomUUID().toString)

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
      case (startOffsets, endOffsets) =>
        val _endOffsets = endOffsets.map { case (tp, o)          => tp.partition -> o }
        val fullStart   = startOffsets.filter { case (tp, start) => start < endOffsets(tp) }
        val startFrom = offset match {
          case Offset.Earliest         => fullStart
          case Offset.Latest           => Map.empty[TopicPartition, Long]
          case Offset.FromBeginning(v) => fullStart.map { case (tp, o) => tp -> (o + v) }
          case Offset.FromEnd(v) =>
            endOffsets.filter { case (p, end) => startOffsets(p) < end }.map {
              case (tp, o) => tp -> (if (o < v) o else o - v)
            }
        }
        SortedSet.from(startFrom.keySet).toNes.foldMap { partitions =>
          consumer.evalTap { c =>
            for {
              _ <- c.assign(partitions)
              _ <- startFrom.toList.traverse_ { case (tp, o) => c.seek(tp, o) }
            } yield ()
          }.flatMap { c =>
            c.partitionedStream.map { ps =>
              ps.takeThrough { cr =>
                (cr.record.offset + 1) < _endOffsets(cr.record.partition)
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
          }.take(startFrom.size)
            .parJoinUnbounded
            .through(stdoutLines(blocker))
        }
    }

}
