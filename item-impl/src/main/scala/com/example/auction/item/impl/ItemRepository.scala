package com.example.auction.item.impl

import java.util.UUID

import akka.stream.Materializer
import com.example.auction.utils.PagingState
import com.example.auction.item.api
import com.lightbend.lagom.scaladsl.persistence.ReadSideProcessor
import com.lightbend.lagom.scaladsl.persistence.slick.SlickReadSide
import slick.jdbc.JdbcProfile
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.meta.MTable

import scala.concurrent.{ExecutionContext, Future}

private[impl] class ItemRepository(profile: JdbcProfile, db: Database)(implicit ec: ExecutionContext, mat: Materializer) {

  import profile.api._

  def executeGetItemsForUser(creatorId: UUID, status: ItemStatus.Status, page: Int, itemsPerPage: Int): Future[PagingState[ItemSummary]] = {
    db.run {
      for {
        count <- countItemsByCreatorInStatus(creatorId, status)
        items <- selectItemsByCreatorInStatus(creatorId, status, (page - 1) * itemsPerPage, itemsPerPage)
      } yield {
        PagingState(items, page, itemsPerPage, count)
      }
    }
  }

  def insert(itemSummary: ItemSummary) = {
    itemSummaries += itemSummary
  }

  def updateStatus(itemId: UUID, status: ItemStatus.Status): slick.dbio.DBIO[Int] = {
    (for {
      item <- itemSummaries
      if item.itemId === itemId
    } yield item.status)
      .update(status)
  }

  def createTable(implicit ec: ExecutionContext) = {
    MTable.getTables.flatMap { tables =>
      if (tables.forall(_.name.name != itemSummaries.baseTableRow.tableName)) {
        itemSummaries.schema.create
      } else {
        SimpleDBIO(_ => ())
      }
    }
  }

  private def byCreatorInStatus(creatorId: UUID, status: ItemStatus.Status) =
    itemSummaries
      .filter(_.creatorId === creatorId)
      .filter(_.status === status)

  private def countItemsByCreatorInStatus(creatorId: UUID, status: ItemStatus.Status) =
    byCreatorInStatus(creatorId, status).length.result

  private def selectItemsByCreatorInStatus(creatorId: UUID, status: ItemStatus.Status, offset: Int, limit: Int) = {
    byCreatorInStatus(creatorId, status)
      .sortBy(_.itemId)
      .drop(offset)
      .take(limit)
      .result
  }

  private implicit val itemStatusColumnType: BaseColumnType[ItemStatus.Status] =
    MappedColumnType.base[ItemStatus.Status, Int](_.id, ItemStatus.apply)


  private class ItemSummaryTable(tag: Tag) extends Table[ItemSummary](tag, "item_summary") {
    def * = (itemId, creatorId, title, currencyId, reservePrice, status) <> (ItemSummary.tupled, ItemSummary.unapply)

    def itemId = column[UUID]("item_id", O.PrimaryKey)
    def creatorId = column[UUID]("creator_id")
    def title = column[String]("title")
    def currencyId = column[String]("currency_id")
    def reservePrice = column[Int]("reserve_price")
    def status = column[ItemStatus.Status]("status")

    def creatorIdStatusIndex = index("idx_item_summary_creator_id_status", (creatorId, status), unique = false)
  }

  private val itemSummaries = TableQuery[ItemSummaryTable]

}

case class ItemSummary(
  itemId: UUID,
  creatorId: UUID,
  title: String,
  currencyId: String,
  reservePrice: Int,
  status: ItemStatus.Status
) {
  def toApi: api.ItemSummary = api.ItemSummary(
    itemId, title, currencyId, reservePrice, ItemStatus.toApi(status)
  )
}

private[impl] class ItemEventProcessor(readSide: SlickReadSide, repo: ItemRepository)(implicit ec: ExecutionContext)
    extends ReadSideProcessor[ItemEvent] {

  def buildHandler = {
    readSide.builder[ItemEvent]("itemEventOffset")
        .setGlobalPrepare(repo.createTable)
        .setEventHandler[ItemCreated](e => insertItem(e.event.item))
        .setEventHandler[AuctionStarted](e => updateItemSummaryStatus(e.entityId, ItemStatus.Auction))
        .setEventHandler[AuctionFinished](e => updateItemSummaryStatus(e.entityId, ItemStatus.Completed))
        .build
  }

  def aggregateTags = ItemEvent.Tag.allTags

  private def insertItem(item: Item) = {
    repo.insert(ItemSummary(item.id, item.creator, item.title, item.currencyId, item.reservePrice, item.status))
  }

  private def updateItemSummaryStatus(itemId: String, status: ItemStatus.Status) = {
    val itemUuid = UUID.fromString(itemId)
    repo.updateStatus(itemUuid, status)
  }
}