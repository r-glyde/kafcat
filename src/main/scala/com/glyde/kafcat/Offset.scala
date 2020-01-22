package com.glyde.kafcat

sealed trait Offset extends Product with Serializable

object Offset {

  case object Earliest                        extends Offset
  case object Latest                          extends Offset
  final case class FromBeginning(value: Long) extends Offset
  final case class FromEnd(value: Long)       extends Offset

}
