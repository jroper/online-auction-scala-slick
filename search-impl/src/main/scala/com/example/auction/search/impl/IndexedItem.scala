package com.example.auction.search.impl

import java.util.UUID

import play.api.libs.json.{Format, Json}


case class IndexedItem(
  itemId: UUID,
  creatorId: Option[UUID] = None,
  title: Option[String] = None,
  description: Option[String] = None,
  currencyId: Option[String] = None,
  price: Option[Int] = None,
  status: Option[String] = None,
  winner: Option[UUID] = None
)


object IndexedItem {
  implicit val format: Format[IndexedItem] = Json.format
}
