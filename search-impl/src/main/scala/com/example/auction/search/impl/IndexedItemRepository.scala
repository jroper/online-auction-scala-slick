package com.example.auction.search.impl

import java.util.UUID

import akka.Done
import com.example.auction.item.api.ItemStatus
import slick.backend.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.jdbc.meta.MTable

import scala.concurrent.{ExecutionContext, Future}

class IndexedItemRepository(databaseConfig: DatabaseConfig[JdbcProfile])(implicit ec: ExecutionContext) extends TsVectorSupport {

  override val profile = databaseConfig.profile
  private val db = databaseConfig.db

  import profile.api._


  private class IndexedItemTable(tag: Tag) extends Table[IndexedItem](tag, "indexed_item") {
    def itemId = column[UUID]("item_id", O.PrimaryKey)

    def creatorId = column[Option[UUID]]("creator_id")

    def title = column[Option[String]]("title")

    def description = column[Option[String]]("description")

    def currencyId = column[Option[String]]("currency_id")

    def price = column[Option[Int]]("price")

    def status = column[Option[String]]("status")

    def winner = column[Option[UUID]]("winner")

    def weightedTsv = column[Option[TsVector]]("weighted_tsv", O.SqlType("tsvector"))

    override def * = (itemId, creatorId, title, description, currencyId, price, status, winner) <> ((IndexedItem.apply _).tupled, IndexedItem.unapply)
  }

  private val indexedItems = TableQuery[IndexedItemTable]

  private val addTsvColumn =
    sqlu"""
      ALTER TABLE indexed_item
      ADD COLUMN weighted_tsv tsvector;
    """

  private val tsvIndex =
    sqlu"""
      CREATE INDEX idx_indexed_item_weighted_tsv
        ON indexed_item
        USING gin(weighted_tsv);
    """

  private val tsvFunction =
    sqlu"""
      CREATE FUNCTION indexed_item_weighted_tsv_trigger() RETURNS trigger AS $$$$
         begin
           new.weighted_tsv :=
              setweight(to_tsvector('english', COALESCE(new.title,'')), 'A') ||
              setweight(to_tsvector('english', COALESCE(new.description,'')), 'B');
           return new;
         end
      $$$$ LANGUAGE plpgsql;
    """

  private val tsvTrigger =
    sqlu"""
      CREATE TRIGGER trigger_indexed_item_tsv BEFORE INSERT OR UPDATE
        ON indexed_item
        FOR EACH ROW EXECUTE PROCEDURE indexed_item_weighted_tsv_trigger();
    """

  private def doCreateTables = {
    MTable.getTables.flatMap { tables =>
      if (tables.forall(_.name.name != indexedItems.baseTableRow.tableName)) {
        DBIO.seq(
          indexedItems.schema.create,
          addTsvColumn,
          tsvFunction,
          tsvIndex,
          tsvTrigger
        )
      } else {
        SimpleDBIO(_ => ())
      }
    }
  }

  def createTables(): Future[Unit] = {
    db.run(doCreateTables)
  }

  private def createQuery(status: String, terms: Option[String], price: Option[(Int, String)]) = {
    val filteredByTerms = terms match {
      case Some(ts) => indexedItems.filter(_.weightedTsv @@ toTsquery(ts))
      case None => indexedItems
    }
    price match {
      case Some((p, currencyId)) => filteredByTerms.filter(item => item.price < p && item.currencyId === currencyId)
      case None => filteredByTerms
    }
  }

  def query(status: String, terms: Option[String], price: Option[(Int, String)], pageNo: Int, pageSize: Int): Future[(Int, Seq[IndexedItem])] = {
    db.run {
      for {
        numItems <- createQuery(status, terms, price)
          .length
          .result
        items <- createQuery(status, terms, price)
          .drop(pageNo * pageSize)
          .take(pageSize)
          .result
      } yield (numItems, items)
    }
  }

  private def indexedItem(itemId: UUID) = indexedItems.filter(_.itemId === itemId)

  def updateItemAuctionStarted(itemId: UUID): Future[Done] = {
    db.run {
      indexedItem(itemId)
        .map(_.status)
        .update(Some(ItemStatus.Auction.toString))
        .transactionally
        .map(_ => Done)
    }
  }

  def upsertItem(itemId: UUID, creatorId: UUID, title: String, description: String, currencyId: String,
    status: ItemStatus.Status): Future[Done] = {
    db.run {
      indexedItems
        .map(i => (i.itemId, i.creatorId, i.title, i.description, i.currencyId, i.status))
        .insertOrUpdate((itemId, Some(creatorId), Some(title), Some(description), Some(currencyId), Some(status.toString)))
        .transactionally
        .map(_ => Done)
    }
  }

  def deleteItem(itemId: UUID): Future[Done] = {
    db.run {
      indexedItem(itemId)
        .delete
        .transactionally
        .map(_ => Done)
    }
  }

  def updateItemPrice(itemId: UUID, price: Int): Future[Done] = {
    db.run {
      indexedItems
        .map(i => (i.itemId, i.price))
        .insertOrUpdate((itemId, Some(price)))
        .transactionally
        .map(_ => Done)
    }
  }
}
