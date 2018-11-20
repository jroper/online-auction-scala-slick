package com.example.auction.bidding.impl

import java.sql.Timestamp
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.{Date, UUID}

import akka.Done
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import com.lightbend.lagom.scaladsl.persistence.slick.SlickReadSide
import com.lightbend.lagom.scaladsl.persistence.{EventStreamElement, PersistentEntityRegistry, ReadSideProcessor}
import org.slf4j.LoggerFactory
import slick.dbio.{DBIOAction, Effect, NoStream}
import slick.jdbc.JdbcProfile
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.meta.MTable

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
  * Maintains a read side view of all auctions that gets used to schedule FinishBidding events.
  */
class AuctionSchedulerProcessor(readSide: SlickReadSide, repo: AuctionSchedulerRepository)(implicit ec: ExecutionContext)
  extends ReadSideProcessor[AuctionEvent] {

  def buildHandler = {
    readSide.builder[AuctionEvent]("auctionSchedulerOffset")
      .setGlobalPrepare(repo.createTable)
      .setEventHandler[AuctionStarted](insertAuction)
      .setEventHandler[BiddingFinished.type](deleteAuction)
      .setEventHandler[AuctionCancelled.type](deleteAuction)
      .build()
  }

  private def insertAuction(started: EventStreamElement[AuctionStarted]) = {
    repo.insertAuction(AuctionSchedule(UUID.fromString(started.entityId), started.event.auction.endTime))
  }

  private def deleteAuction(event: EventStreamElement[_]) = {
    repo.deleteAuction(UUID.fromString(event.entityId))
  }

  def aggregateTags = AuctionEvent.Tag.allTags
}

case class AuctionSchedule(itemId: UUID, endAuction: Instant)

class AuctionSchedulerRepository(profile: JdbcProfile) {

  import profile.api._

  private implicit val instantColumnType: BaseColumnType[Instant] = MappedColumnType.base[Instant, Timestamp](
    {
      case null => null
      case instant => Timestamp.from(instant)
    },
    {
      case null => null
      case timestamp => timestamp.toInstant
    }
  )

  private val currentInstant = SimpleLiteral[Instant]("CURRENT_TIMESTAMP")

  private class AuctionScheduleTable(tag: Tag)
    extends Table[AuctionSchedule](tag, "auction_schedule") {

    def * = (itemId, endAuction) <> (AuctionSchedule.tupled, AuctionSchedule.unapply)

    def itemId = column[UUID]("item_id", O.PrimaryKey)

    def endAuction = column[Instant]("end_auction")

    def endAuctionIndex = index("idx_auction_schedule_end_auction", endAuction, unique = false)
  }

  private val auctions = TableQuery[AuctionScheduleTable]

  def selectFinishedAuctions: slick.dbio.StreamingDBIO[Seq[AuctionSchedule], AuctionSchedule] =
    auctions.filter(_.endAuction < currentInstant).result
  def insertAuction(auctionSchedule: AuctionSchedule) = {
    auctions += auctionSchedule
  }
  def deleteAuction(itemId: UUID): slick.dbio.DBIO[Int] = {
    auctions.filter(_.itemId === itemId)
      .delete
  }
  def createTable(implicit ec: ExecutionContext) = {
    MTable.getTables.flatMap { tables =>
      if (tables.forall(_.name.name != auctions.baseTableRow.tableName)) {
        auctions.schema.create
      } else {
        SimpleDBIO(_ => ())
      }
    }
  }
}

class AuctionScheduler(db: Database, repo: AuctionSchedulerRepository, system: ActorSystem, registry: PersistentEntityRegistry)(implicit val mat: Materializer, ec: ExecutionContext) {
  private val log = LoggerFactory.getLogger(classOf[AuctionScheduler])

  {
    val finishBiddingDelay = system.settings.config.getDuration("auctionSchedulerDelay", TimeUnit.MILLISECONDS).milliseconds
    system.scheduler.schedule(finishBiddingDelay, finishBiddingDelay) {
      checkFinishBidding()
    }
  }


  /**
    * Check whether there are any auctions that are due to finish, and if so, send a command to finish them.
    */
  private def checkFinishBidding() = {
    Source.fromPublisher(db.stream(repo.selectFinishedAuctions))
      .mapAsyncUnordered(4) { item =>
        registry.refFor[AuctionEntity](item.itemId.toString)
          .ask(FinishBidding)
      }.recover {
        case e =>
          log.warn("Error running finish bidding query", e)
          Done
    }.runWith(Sink.ignore)
  }
}
