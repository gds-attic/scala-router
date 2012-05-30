package uk.gov.gds.router.integration

class MultiHomingTest extends RouterIntegrationTestSetup {

  test("hostname routes to different application"){
    createAlsoSupportedTestApplication()

    val mainHomeResponse = get("/route/mainhost/fulltest/test.html")
    mainHomeResponse.status should be(200)

    mainHomeResponse.body.contains("router also supported full route") should be(false)
    mainHomeResponse.body.contains("router flat route") should be(true)

    val response = get("/route/alsosupported/fulltest/test.html")
    response.status should be(200)

    response.body.contains("router also supported full route") should be(true)
    response.body.contains("router flat route") should be(false)
  }


  test("different hosts can have the same prefix route"){

    val alsoSupportedApplicationId = createAlsoSupportedTestApplication()

    var creationResponse = post("/routes/mainhost/a-prefix-route", Map("application_id" -> applicationId, "route_type" -> "prefix"))
    creationResponse.status should be(201)

    val mainHostResponse = get("/route/mainhost/a-prefix-route/foo")
    mainHostResponse.status should be(200)
    mainHostResponse.body.contains("foo!") should be(true)
    mainHostResponse.body.contains("oof!") should be(false)


    creationResponse = post("/routes/alsosupported/a-prefix-route", Map("application_id" -> alsoSupportedApplicationId, "route_type" -> "prefix")) //this is not creating
    creationResponse.status should be(201)

    val alsoSupportedHostResponse = get("/route/alsosupported/a-prefix-route/foo") //so this is defaulting to main? why?
    alsoSupportedHostResponse.status should be(200)
    alsoSupportedHostResponse.body.contains("foo!") should be(false)
    alsoSupportedHostResponse.body.contains("oof!") should be(true)
  }

}