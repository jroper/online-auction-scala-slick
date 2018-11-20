package com.example.auction.search.impl

import akka.Done
import akka.stream.scaladsl.Flow
import com.example.auction.bidding.api.{BidEvent, BidPlaced, BiddingFinished, BiddingService}
import com.example.auction.item.api._

import scala.concurrent.{ExecutionContext, Future}

class BrokerEventConsumer(repo: IndexedItemRepository,
  itemService: ItemService,
  biddingService: BiddingService
)(implicit ec: ExecutionContext) {

  private val itemEvents = itemService.itemEvents
  private val bidEvents = biddingService.bidEvents

  itemEvents.subscribe.withGroupId("search-service")
    .atLeastOnce(Flow[ItemEvent]
      .mapAsync(1) {

        case AuctionStarted(itemId, _, _, _, _, _) =>
          repo.updateItemAuctionStarted(itemId)

        case AuctionFinished(itemId, _) =>
          repo.deleteItem(itemId)

        case ItemUpdated(itemId, creatorId, title, description, currencyId, status) =>
          repo.upsertItem(itemId, creatorId, title, description, currencyId, status)

        case _ => Future.successful(Done)
      })

  bidEvents.subscribe.withGroupId("search-service")
    .atLeastOnce(Flow[BidEvent].mapAsync(1) {

      case BidPlaced(itemId, bid) =>
        repo.updateItemPrice(itemId, bid.price)

      case BiddingFinished(itemId, _) =>
        repo.deleteItem(itemId)

      case _ => Future.successful(Done)
    })
}
