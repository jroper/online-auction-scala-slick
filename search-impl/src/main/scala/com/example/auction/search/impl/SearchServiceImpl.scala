package com.example.auction.search.impl

import com.example.auction.item.api.ItemStatus
import com.example.auction.search.api.{SearchItem, SearchRequest, SearchResponse, SearchService}
import com.lightbend.lagom.scaladsl.api.ServiceCall

import scala.concurrent.ExecutionContext

class SearchServiceImpl(repo: IndexedItemRepository)(implicit ec: ExecutionContext) extends SearchService {

  override def search(pageNo: Int, pageSize: Int): ServiceCall[SearchRequest, SearchResponse] = ServiceCall { query =>
    repo.query(ItemStatus.Auction.toString, query.keywords, (query.maxPrice zip query.currency).headOption,
      pageNo, pageSize)
      .map {
        case (numItems, items) =>
          SearchResponse(items.map(toApi), pageSize, pageNo, numItems)
      }
  }

  private def toApi(ii: IndexedItem): SearchItem = {
    SearchItem(
      ii.itemId,
      ii.creatorId.get,
      ii.title.get,
      ii.description.get,
      ii.status.get,
      ii.currencyId.get,
      ii.price)
  }
}
