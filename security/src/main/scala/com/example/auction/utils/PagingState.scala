package com.example.auction.utils

import play.api.libs.json._
import play.api.libs.functional.syntax._

case class PagingState[T](
  items: Seq[T],
  page: Int,
  itemsPerPage: Int,
  count: Int
) {

  def isEmpty: Boolean = items.isEmpty
  def isLast: Boolean = itemsPerPage * page >= count
  def map[U](f: T => U): PagingState[U] = copy(items = items.map(f))
}

object PagingState {
  implicit def format[T: Format]: Format[PagingState[T]] = Json.format
}




