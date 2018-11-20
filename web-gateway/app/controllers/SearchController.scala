package controllers

import java.util.UUID

import com.example.auction.search.api.{SearchRequest, SearchService}
import com.example.auction.user.api.UserService
import play.api.data.Forms._
import play.api.data.Form
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

class SearchController(
  userService: UserService,
  searchService: SearchService,
  controllerComponents: ControllerComponents
)(implicit ec: ExecutionContext)
  extends AbstractAuctionController(userService, controllerComponents) {

  val PageSize = 15

  def search = Action.async { implicit request =>
    withUser { user =>
      loadNav(user).flatMap { implicit nav =>
        val form = SearchItemForm.bind
        form.fold(
          errorForm => Future.successful(Ok(views.html.searchItem(errorForm))),
          searchItemForm => {
            searchService.search(searchItemForm.pageNumber, PageSize).invoke(SearchRequest(
              searchItemForm.keywords, searchItemForm.maximumPrice.filter(_ > 0)
                .map(price => searchItemForm.maximumPriceCurrency.toPriceUnits(price.toDouble)),
              Some(searchItemForm.maximumPriceCurrency.name)
            )).map { results =>
              Ok(views.html.searchItem(form,
                Some(PaginatedSequence(results.items, results.pageNo, results.pageSize, results.numResults))))
            }
          }
        )
      }
    }
  }

  def searchForm = Action.async { implicit request =>
    withUser { user =>
      loadNav(user).map { implicit nav =>
        Ok(views.html.searchItem(SearchItemForm.empty))
      }
    }
  }

}

case class SearchItemForm(pageNumber: Int, keywords: Option[String], maximumPriceCurrency: Currency, maximumPrice: Option[BigDecimal])

object SearchItemForm {

  import FormMappings._

  private val form = Form(
    mapping(
      "pageNumber" -> default(number, 0),
      "keywords" -> optional(text),
      "maximumPriceCurrency" -> default(currency, Currency.USD),
      "maximumPrice" -> optional(
        bigDecimal.verifying("invalid.maximumPrice", _ > 0)
      )
    )(SearchItemForm.apply)(SearchItemForm.unapply)
  )

  def empty: Form[SearchItemForm] = fill(SearchItemForm(0, None, Currency.USD, None))

  def fill(searchItemForm: SearchItemForm): Form[SearchItemForm] = form.fill(searchItemForm)

  def bind(implicit request: Request[AnyContent]): Form[SearchItemForm] = {
    form.bindFromRequest()
  }
}
