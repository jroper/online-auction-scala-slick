package com.example.auction.item.impl

import java.util.UUID

import akka.persistence.query.Offset
import com.example.auction.item.api.ItemService
import com.example.auction.item.api
import com.example.auction.security.ServerSecurity._
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.api.transport.{Forbidden, NotFound}
import com.lightbend.lagom.scaladsl.broker.TopicProducer
import com.lightbend.lagom.scaladsl.persistence.{EventStreamElement, PersistentEntityRegistry}
import com.lightbend.lagom.scaladsl.server.ServerServiceCall

import scala.concurrent.{ExecutionContext, Future}

class ItemServiceImpl(registry: PersistentEntityRegistry, itemRepository: ItemRepository)(implicit ec: ExecutionContext) extends ItemService {

  private val DefaultPageSize = 10

  override def createItem = authenticated(userId => ServerServiceCall { item =>
    if (userId != item.creator) {
      throw Forbidden("User " + userId + " can't created an item on behalf of " + item.creator)
    }
    val itemId = UUID.randomUUID()
    val pItem = Item(itemId, item.creator, item.title, item.description, item.currencyId, item.increment,
      item.reservePrice, None, ItemStatus.Created, item.auctionDuration, None, None, None)
    entityRef(itemId).ask(CreateItem(pItem)).map { _ =>
      convertItem(pItem)
    }
  })

  override def startAuction(id: UUID) = authenticated(userId => ServerServiceCall { _ =>
    entityRef(id).ask(StartAuction(userId))
  })

  override def getItem(id: UUID) = ServerServiceCall { _ =>
    entityRef(id).ask(GetItem).map {
      case Some(item) => convertItem(item)
      case None => throw NotFound("Item " + id + " not found");
    }
  }

  override def getItemsForUser(id: UUID, status: api.ItemStatus.Status, page: Int) = ServiceCall { _ =>
    itemRepository.executeGetItemsForUser(id, ItemStatus.fromApi(status), page, DefaultPageSize)
      .map(_.map(_.toApi))
  }

  override def itemEvents = TopicProducer.taggedStreamWithOffset(ItemEvent.Tag.allTags.toList) { (tag, offset) =>
    registry.eventStream(tag, offset)
      .filter {
        _.event match {
          case x@(_: ItemCreated | _: AuctionStarted | _: AuctionFinished) => true
          case _ => false
        }
      }.mapAsync(1)(convertEvent)
  }

  private def convertItem(item: Item): api.Item = {
    api.Item(Some(item.id), item.creator, item.title, item.description, item.currencyId, item.increment,
      item.reservePrice, item.price, convertStatus(item.status), item.auctionDuration, item.auctionStart, item.auctionEnd,
      item.auctionWinner)
  }

  private def convertStatus(status: ItemStatus.Status): api.ItemStatus.Status = {
    status match {
      case ItemStatus.Created => api.ItemStatus.Created
      case ItemStatus.Auction => api.ItemStatus.Auction
      case ItemStatus.Completed => api.ItemStatus.Completed
      case ItemStatus.Cancelled => api.ItemStatus.Cancelled
    }
  }


  private def convertEvent(eventStreamElement: EventStreamElement[ItemEvent]): Future[(api.ItemEvent, Offset)] = {
    eventStreamElement match {
      case EventStreamElement(itemId, AuctionStarted(_), offset) =>
        entityRefString(itemId).ask(GetItem).map {
          case Some(item) =>
            (api.AuctionStarted(
              itemId = item.id,
              creator = item.creator,
              reservePrice = item.reservePrice,
              increment = item.increment,
              startDate = item.auctionStart.get,
              endDate = item.auctionEnd.get
            ), offset)
        }
      case EventStreamElement(itemId, AuctionFinished(winner, price), offset) =>
        entityRefString(itemId).ask(GetItem).map {
          case Some(item) =>
            (api.AuctionFinished(
              itemId = item.id,
              item = convertItem(item)
            ), offset)
        }
      case EventStreamElement(itemId, ItemCreated(item), offset) =>
        Future.successful {
          (api.ItemUpdated(
            itemId = item.id,
            creator = item.creator,
            title = item.title,
            description = item.description,
            currencyId = item.currencyId,
            status = convertStatus(item.status)
          ), offset)
        }
    }
  }


  private def entityRef(itemId: UUID) = entityRefString(itemId.toString)

  private def entityRefString(itemId: String) = registry.refFor[ItemEntity](itemId)

}
