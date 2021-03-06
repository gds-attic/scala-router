package uk.gov.gds.router.model

import uk.gov.gds.router.repository.application.Applications
import uk.gov.gds.router.util.IncomingPath

trait HasIdentity {
  def id: String
}

case class Application(application_id: String,
                       backend_url: String) extends HasIdentity {
  def id = application_id
}

case class Route(incoming_path: String,
                 application_id: String,
                 route_type: String,
                 route_action: String = "proxy",
                 properties: Map[String,String] = Map.empty) extends HasIdentity {

  val application = Applications.load(application_id).getOrElse(throw new Exception("Can't find application for route " + this))

  if ("prefix" == route_type && IncomingPath.prefix(incoming_path) != IncomingPath.path(incoming_path))
    throw new RuntimeException("Invalid route: the prefix route must be a single path segment, e.g. '/prefix' or '/host/www.example.com/prefix'" + " : " + IncomingPath.prefix(incoming_path) + " : " +IncomingPath.path(incoming_path))

  def proxyType = route_type match {
    case "full" => FullRoute
    case "prefix" => PrefixRoute
    case _ => throw new Exception("Unknown route type")
  }

  def routeAction = route_action match {
    case "proxy" => Proxy
    case "gone" => Gone
    case "redirect" => Redirect
    case _ => throw new Exception("Unknown proxy type " + route_action)
  }

  def id = incoming_path
}

object ApplicationForGoneRoutes extends Application("router-gone", "todo:remove this")

object ApplicationForRedirectRoutes extends Application("router-redirect", "todo:remove this")

sealed abstract class RouteType

case object FullRoute extends RouteType

case object PrefixRoute extends RouteType

sealed abstract class RouteAction

case object Proxy extends RouteAction

case object Gone extends RouteAction

case object Redirect extends RouteAction


