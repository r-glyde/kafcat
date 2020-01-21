package com.glyde.kafcat

sealed trait SupportedType extends Product with Serializable

object SupportedType {
  case object String extends SupportedType
  case object Avro   extends SupportedType
}
