package com.example.auction.search

import com.example.auction.bidding.api.BiddingService
import com.example.auction.item.api.ItemService
import com.example.auction.search.api.SearchService
import com.example.auction.search.impl.{BrokerEventConsumer, IndexedItemRepository, SearchServiceImpl}
import com.lightbend.lagom.scaladsl.broker.kafka.LagomKafkaClientComponents
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import com.lightbend.lagom.scaladsl.server.status.MetricsServiceComponents
import com.lightbend.lagom.scaladsl.server.{LagomApplication, LagomApplicationContext, LagomApplicationLoader}
import com.softwaremill.macwire._
import com.lightbend.rp.servicediscovery.lagom.scaladsl.LagomServiceLocatorComponents
import play.api.db.slick.{DbName, SlickComponents}
import play.api.libs.ws.ahc.AhcWSComponents
import slick.jdbc.JdbcProfile

abstract class SearchApplication(context: LagomApplicationContext) extends LagomApplication(context)
  with AhcWSComponents
  with LagomKafkaClientComponents
  with SlickComponents
  with MetricsServiceComponents {

  lazy val databaseConfig = slickApi.dbConfig[JdbcProfile](DbName("default"))

  lazy val itemService = serviceClient.implement[ItemService]
  lazy val bidService = serviceClient.implement[BiddingService]
  lazy val indexedItemRepository = wire[IndexedItemRepository]

  override lazy val lagomServer = serverFor[SearchService](wire[SearchServiceImpl])

  indexedItemRepository.createTables().map { _ =>
    wire[BrokerEventConsumer]
  }.onFailure {
    case t => t.printStackTrace()
  }
}

class SearchApplicationLoader extends LagomApplicationLoader {
  override def load(context: LagomApplicationContext) =
    new SearchApplication(context) with LagomServiceLocatorComponents

  override def loadDevMode(context: LagomApplicationContext) =
    new SearchApplication(context) with LagomDevModeComponents
  
  override def describeService = Some(readDescriptor[SearchService])
}
