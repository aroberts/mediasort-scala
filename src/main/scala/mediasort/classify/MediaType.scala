package mediasort.classify

sealed trait MediaType
object MediaType {
  case object TV extends MediaType
  case object Movie extends MediaType
  case object Music extends MediaType
  case object LosslessMusic extends MediaType
  case object Other extends MediaType
}
