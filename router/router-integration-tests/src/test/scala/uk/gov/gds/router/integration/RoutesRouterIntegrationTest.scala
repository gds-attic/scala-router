package uk.gov.gds.router.integration

import uk.gov.gds.router.util.JsonSerializer._
import uk.gov.gds.router.model._

class RoutesRouterIntegrationTest
  extends RouterIntegrationTestSetup {

  test("Can create routes using put") {
    when("We create a route to our backend application with a PUT")
    val response = put("/routes/route-created-with-put", Map("application_id" -> applicationId, "route_type" -> "full"))

    then("We should get a 201 response signifying sucessful creation")
    response.status should be(201)
  }

  test("canot create route on application that does not exist") {
    when("We attempt to create a route for a backend application that does not exists")
    val response = put("/routes/this-route-does-not-exist", Map("application_id" -> "this-app-does-not-exist", "incoming_path" -> "foo", "route_type" -> "foo"))

    then("We should fail with a server error")
    response.status should be(500)
  }

  test("Can create / update / delete full routes") {
    given("A unique route ID that is not present in the router")
    val routeId = uniqueIdForTest

    when("We create that route with a route type of full")
    var response = post("/routes/" + routeId,
      Map(
        "application_id" -> applicationId,
        "route_type" -> "full"))

    then("We should get a 201 response with JSON representing the created router")
    response.status should be(201)
    var route = fromJson[Route](response.body)
    route.application_id should be(applicationId)
    route.incoming_path should be(routeId)
    route.proxyType should be(FullRoute)
    route.route_action should be("proxy")

    then("We should be able to retrieve the route information through the router API")
    response = get("/routes/" + routeId)
    response.status should be(200)
    route = fromJson[Route](response.body)
    route.application_id should be(applicationId)
    route.incoming_path should be(routeId)

    given("A newly created application")
    val newApplicationId = createTestApplication("update-application")

    when("We attempt to update the previously created route to point to this new application")
    response = put("/routes/" + routeId,
      Map(
        "application_id" -> newApplicationId,
        "route_type" -> "full"))

    then("We should get a response signifiying that the route has been updated")
    response.status should be(200)
    route = fromJson[Route](response.body)
    route.application_id should be(newApplicationId)
    route.incoming_path should be(routeId)

    when("We delete the route")
    response = delete("/routes/" + route.incoming_path)

    then("The route should be gone")
    val deletedRoute = fromJson[Route](response.body)
    response.status should be(200)
    deletedRoute.route_action should be("gone")
    deletedRoute.application should be(ApplicationForGoneRoutes)

    when("We try to reload the route")
    response = get("/routes/" + routeId)

    then("the route still should be gone")
    fromJson[Route](response.body).route_action should be("gone")
  }

  test("Can create / update / delete prefix routes") {
    given("A unique route ID that is not present in the router")
    val routeId = uniqueIdForTest

    when("We create that route with a route type of prefix")
    var response = post("/routes/" + routeId,
      Map(
        "application_id" -> applicationId,
        "route_type" -> "prefix"))

    then("We should get a 201 response with JSON representing the created route")
    response.status should be(201)
    var route = fromJson[Route](response.body)
    route.application_id should be(applicationId)
    route.incoming_path should be(routeId)
    route.proxyType should be(PrefixRoute)
    route.route_action should be("proxy")

    then("We should be able to retreive the route information through the router API")
    response = get("/routes/" + routeId)
    response.status should be(200)
    route = fromJson[Route](response.body)
    route.application_id should be(applicationId)
    route.incoming_path should be(routeId)

    given("A newly created application")
    val newApplicationId = createTestApplication("update-application")

    when("We attempt to update the previously created route to point to this new application")
    response = put("/routes/" + routeId,
      Map(
        "application_id" -> newApplicationId,
        "route_type" -> "prefix"))

    then("We should get a response signifiying that the route has been updated")
    response.status should be(200)
    route = fromJson[Route](response.body)
    route.application_id should be(newApplicationId)
    route.incoming_path should be(routeId)

    when("We delete the route")
    response = delete("/routes/" + route.incoming_path)

    then("The route should be gone")
    response.status should be(204)

    when("We try to reload the route")
    response = get("/routes/" + routeId)

    then("the route still should be gone")
    response.status should be(404)
  }

  test("When a full route is deleted via the API it returns a 410 when accessed through the proxy") {
    given("The test harness application created with some default routes")
    when("we access a known full route")

    val response = get("/route/fulltest/test.html")

    then("the response should be a 200 with the contents from the backend application")
    response.status should be(200)
    response.body contains ("router flat route") should be(true)

    when("We delete the route through the API")
    val deleteResponse = delete("/routes/fulltest/test.html")

    then("When we examine the route through the API its route_action should be 'gone'")
    val route = fromJson[Route](deleteResponse.body)
    route.route_action should be("gone")

    then("and the route should not be associated with an application")
    route.application_id should be(ApplicationForGoneRoutes.application_id)
    route.application should be(ApplicationForGoneRoutes)

    then("and we retrieve the route again we should get a 410 gone response")
    val secondGetResponse = get("/route/fulltest/test.html")

    secondGetResponse.status should be(410)
    secondGetResponse.body contains ("router flat route") should be(false)
  }

  test("a redirect full route will give a 301 status") {
    given("A unique route ID that is not present in the router")
    val routeId = uniqueIdForTest

    when("We create that route with a route type of full, a route action of redirect and a location")
    var response = put("/routes/" + routeId,
      Map(
        "route_type" -> "full",
        "route_action" -> "redirect",
        "location" -> "/destination/page.html"))

    then("We should be able to retreive the route information through the router API")
    response = get("/route/" + routeId)

    response.status should be(301)

    def header(x: Option[Header]) = x match {
      case Some(header) => header.value
      case None => Unit
    }

    header( response.headers find {_.name == "Location"} ) should be("/destination/page.html")
  }

  test("a redirect route is given the application id of the application for redirect routes") {
    given("A unique route ID that is not present in the router")
    val routeId = uniqueIdForTest

    when("We create that route with a route type of full, a route action of redirect and a location")
    val response = post("/routes/" + routeId,
      Map(
        "route_type" -> "full",
        "route_action" -> "redirect",
        "location" -> "/destination/page.html"))

    then("the application id should be that of the application for redirect routes")
    val route = fromJson[Route](response.body)
    route.application_id should be(ApplicationForRedirectRoutes.id)
  }

  test("a gone route is given the application id of the application for gone routes") {
    given("A unique route ID that is not present in the router")
    val routeId = uniqueIdForTest

    when("We create that route with a route type of full, a route action of gone")
    val response = post("/routes/" + routeId,
      Map(
        "route_type" -> "full",
        "route_action" -> "gone"))

    then("the application id should be that of the application for gone routes")
    val route = fromJson[Route](response.body)
    route.application_id should be(ApplicationForGoneRoutes.id)
  }

  test("a proxy route cannot have a location") {
    given("A unique route ID that is not present in the router")
    val routeId = uniqueIdForTest

    when("We create that route with a route type of full, no route action and a location")
    val response = post("/routes/" + routeId,
      Map(
        "application_id" -> ApplicationForRedirectRoutes.application_id,
        "route_type" -> "full",
        "location" -> "/destination/page.html"))

    then("the server should return an error")
    response.status should be(500)
  }

  test("a gone route cannot have a location") {
    given("A unique route ID that is not present in the router")
    val routeId = uniqueIdForTest

    when("We create that route with a route type of full, a route action of gone and a location")
    val response = post("/routes/" + routeId,
      Map(
        "application_id" -> ApplicationForRedirectRoutes.application_id,
        "route_type" -> "full",
        "route_action" -> "gone",
        "location" -> "/destination/page.html"))

    then("the server should return an error")
    response.status should be(500)
  }

  test("a redirect route must have a location") {
    given("A unique route ID that is not present in the router")
    val routeId = uniqueIdForTest

    when("We create that route with a route type of full, a route action of redirect and no location")
    val response = post("/routes/" + routeId,
      Map(
        "application_id" -> ApplicationForRedirectRoutes.application_id,
        "route_type" -> "full",
        "route_action" -> "redirect"))
    response.status should be(500)
  }

  test("a redirect route cannot have an empty string location") {
    given("A unique route ID that is not present in the router")
    val routeId = uniqueIdForTest

    when("We create that route with a route type of full, a route action of redirect and an empty location")
    val response = post("/routes/" + routeId,
      Map(
        "application_id" -> ApplicationForRedirectRoutes.application_id,
        "route_type" -> "full",
        "route_action" -> "redirect",
        "location" -> ""))
    response.status should be(500)
  }

  test("a prefix redirect route can be updated"){
    given("A unique route ID that is not present in the router")
    val routeId = uniqueIdForTest

    when("We create that route with a route type of prefix, a route action of redirect and a location")
    var response = put("/routes/" + routeId,
      Map(
        "application_id" -> ApplicationForRedirectRoutes.application_id,
        "route_type" -> "prefix",
        "route_action" -> "redirect",
        "location" -> "/redirect"))
    response.status should be(201)

    var createdRoute = fromJson[Route](response.body)
    createdRoute.properties("location") should be("/redirect")

    when("we update that route")
    response = put("/routes/" + routeId,
      Map(
        "application_id" -> ApplicationForRedirectRoutes.application_id,
        "route_type" -> "full",
        "route_action" -> "redirect",
        "location" -> "/another-redirect"))
    response.status should be(200)

    createdRoute = fromJson[Route](response.body)
    createdRoute.properties("location") should be("/another-redirect")
  }

  test("a full redirect route can be updated"){
    given("A unique route ID that is not present in the router")
    val routeId = uniqueIdForTest

    when("We create that route with a route type of full, a route action of redirect and a location")
    var response = put("/routes/" + routeId,
      Map(
        "application_id" -> ApplicationForRedirectRoutes.application_id,
        "route_type" -> "full",
        "route_action" -> "redirect",
        "location" -> "/redirect/route"))
    response.status should be(201)

    var createdRoute = fromJson[Route](response.body)
    createdRoute.properties("location") should be("/redirect/route")

    when("we update that route")
    response = put("/routes/" + routeId,
      Map(
        "application_id" -> ApplicationForRedirectRoutes.application_id,
        "route_type" -> "full",
        "route_action" -> "redirect",
        "location" -> "/redirect/another-route"))
    response.status should be(200)

    createdRoute = fromJson[Route](response.body)
    createdRoute.properties("location") should be("/redirect/another-route")
  }

  test("can proxy requests to and return responses from backend server") {
    var response = get("/route/fulltest/test.html")
    response.status should be(200)
    response.body.contains("router flat route") should be(true)

    response = get("/route/prefixtest/bang/test.html")
    response.status should be(200)
    response.body.contains("router prefix route") should be(true)
  }

  test("can proxy HEAD requests to and return responses from backend server") {
    var response = head("/route/fulltest/test.html")
    response.status should be(200)
    response.body should be("")

    response = head("/route/prefixtest/bang/test.html")
    response.status should be(200)
    response.body should be("")
  }

  test("can post form submissions to backend server") {
    val response = post("/route/test/test-harness", Map("first" -> "sausage", "second" -> "chips"))
    response.status should be(200)
    response.body.contains("first=sausage") should be(true)
    response.body.contains("second=chips") should be(true)
  }

  test("Cannot create prefix routes with more than one path element") {
    val response = post("/routes/invalid/prefix/route", Map("application_id" -> applicationId, "route_type" -> "prefix"))
    response.status should be(500)
  }

  test("Router does not fallback to invalid prefix route when full route cannot be found") {
    post("/routes/someprefix", Map("application_id" -> applicationId, "route_type" -> "prefix"))
    val registered = get("/route/someprefix")
    val unregistered = get("/route/someprefix/unregistered")

    registered.body.contains("prefix route") should be(true)
    unregistered.body.contains("unregsitered") should be(false)
  }

  test("Can create full routes with more than one path element") {
    val response = post("/routes/valid/full/route", Map("application_id" -> applicationId, "route_type" -> "full"))
    response.status should be(201)
  }

  test("can create a full route that overrides an existing prefix route") {
    val creationResponse = post("/routes/a-prefix-route", Map("application_id" -> applicationId, "route_type" -> "prefix"))
    creationResponse.status should be(201)

    val fullRouteResponse = post("/routes/a-prefix-route/foo/bar", Map("application_id" -> applicationId, "route_type" -> "full"))
    fullRouteResponse.status should be(201)
    val createdRoute = fromJson[Route](fullRouteResponse.body)

    createdRoute.incoming_path should be("a-prefix-route/foo/bar")
  }

  test("Cannot create a full route that conflicts with an existing full route") {
    createRoute(routePath = "foo/bar", applicationId = applicationId, routeType = "full")

    val conflictedResponse = createRoute(routePath = "foo/bar", routeType = "full", applicationId = applicationId)
    conflictedResponse.status should be(409)
    val conflictedRoute = fromJson[Route](conflictedResponse.body)

    conflictedRoute.incoming_path should be("foo/bar")
  }

  test("Overlapping prefix routes should be possible and should map to the correct application") {
    val fooApplicationId = createTestApplication
    val footballApplicationId = createTestApplication

    createRoute(routeType = "prefix", routePath = "foo", applicationId = fooApplicationId)

    var response = createRoute(routeType = "full", routePath = "football", applicationId = footballApplicationId)
    response.status should be(201)

    response = get("/route/foo")
    response.body.contains("fooOnly") should be(true)

    response = get("/route/foo/")
    response.body.contains("fooOnly") should be(true)

    response = get("/route/foo/bar")
    response.body.contains("fooOnly") should be(true)

    response = get("/route/football")
    response.body.contains("football") should be(true)
  }

  test("Router returns 404 error page when route not found") {
    val response = get("/route/asdasdasdasdasdasdasdasdasdasdsadas")
    response.status should be(404)
  }


}
