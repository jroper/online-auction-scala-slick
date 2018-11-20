package controllers

case class PaginatedSequence[T](
  items: Seq[T],
  page: Int,
  pageSize: Int,
  count: Int
) {
  def isEmpty: Boolean = items.isEmpty

  def isFirst: Boolean = page == 0

  def isLast: Boolean = count <= (page + 1) * pageSize

  def isPaged: Boolean = count > pageSize

  def pageCount: Int = ((count - 1) / pageSize) + 1

}
