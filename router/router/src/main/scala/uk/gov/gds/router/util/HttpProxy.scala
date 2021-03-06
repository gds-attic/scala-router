package uk.gov.gds.router.util

import org.apache.http.protocol.HTTP
import javax.servlet.http.HttpServletResponse
import org.apache.http.message.BasicNameValuePair
import org.apache.http.client.methods.{HttpUriRequest, HttpPost, HttpGet}
import org.apache.http.HttpResponse
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.params.{HttpConnectionParams, BasicHttpParams}
import org.apache.http.client.params.HttpClientParams
import org.apache.http.conn.ssl.SSLSocketFactory
import uk.gov.gds.router.model.Route
import uk.gov.gds.router.management.ApplicationMetrics.time
import org.apache.http.client.entity.UrlEncodedFormEntity
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import org.apache.http.conn.scheme.{SchemeRegistry, Scheme, PlainSocketFactory}
import uk.gov.gds.router.controller.RequestInfo
import collection.JavaConversions._

object HttpProxy extends Logging {
  private val schemeRegistry = new SchemeRegistry
  private val connectionManager = new ThreadSafeClientConnManager(schemeRegistry)
  private val httpClient = configureHttpClient()
  private val cleanupThread = configureDeadConnectionCleaner()

  private val requestHeadersToFilter = List(
    HTTP.TRANSFER_ENCODING,
    HTTP.CONTENT_LEN,
    HTTP.TARGET_HOST)

  def get(route: Route)(implicit requestInfo: RequestInfo, response: HttpServletResponse) {
    time(route, proxy(new HttpGet(targetUrl(route))))
  }

  def post(route: Route)(implicit requestInfo: RequestInfo, response: HttpServletResponse) {
    val postRequest = new HttpPost(targetUrl(route))

    val params = requestInfo.multiParams.map {
      case (param, values) => values.map(new BasicNameValuePair(param, _))
    }

    postRequest.setEntity(new UrlEncodedFormEntity(params.flatten.toList, "UTF-8"))
    time(route, proxy(postRequest))
  }

  def shutdown() {
    logger.info("Closing down dead connection cleaner")
    cleanupThread.cancel(true)
  }

  private def proxy(request: HttpUriRequest)(implicit requestInfo: RequestInfo, clientResponse: HttpServletResponse) {
    processRequestHeaders(requestInfo, request)
    generateResponse(httpClient.execute(request), clientResponse, request)
  }

  private def processRequestHeaders(requestInfo: RequestInfo, request: HttpUriRequest) {
    requestInfo.headers.filter(h => !requestHeadersToFilter.contains(h._1)).foreach {
      case (name, value) => {
        request.addHeader(name, value)
      }
    }

    request.addHeader("X-GovUK-Router-Request", "true")
    requestInfo.headers.filter(h => h._1 == HTTP.TARGET_HOST).foreach(h => request.addHeader("X-Forwarded-Host", h._2))
  }

  private def generateResponse(targetResponse: HttpResponse, clientResponse: HttpServletResponse, request: HttpUriRequest) {
    val statusCode = targetResponse.getStatusLine.getStatusCode

    clientResponse.setStatus(statusCode)
    targetResponse.getAllHeaders.foreach(h => clientResponse.setHeader(h.getName, h.getValue))

    logger.info("Proxy response " + request.getMethod + " " + request.getURI + " => " + statusCode)

    Option(targetResponse.getEntity) match {
      case Some(entity) => entity.writeTo(clientResponse.getOutputStream)
      case _ => logger.trace("Router detected response with no entity {} {}", targetResponse.getAllHeaders, statusCode)
    }
  }

  private def targetUrl(route: Route)(implicit request: RequestInfo) = {
    val path = IncomingPath.path(request.targetUrl)
    val backend_url = route.application.backend_url
   
    // add path separator for backwards compatability with the database
    // .. but ideally we should concatonate the backend_urls as is ..
    val sep = if (backend_url.endsWith("/") || path.startsWith("/")) "" else "/"
   
    "http://".concat(backend_url).concat(sep).concat(path)
  }

  private def configureHttpClient() = {
    val httpClient = new DefaultHttpClient(connectionManager)
    val httpParams = new BasicHttpParams()

    HttpConnectionParams.setConnectionTimeout(httpParams, 5000);
    HttpConnectionParams.setSoTimeout(httpParams, 5000);
    HttpClientParams.setRedirecting(httpParams, false)

    schemeRegistry.register(new Scheme("http", 80, PlainSocketFactory.getSocketFactory))
    schemeRegistry.register(new Scheme("https", 443, SSLSocketFactory.getSocketFactory))
    connectionManager.setMaxTotal(300)
    connectionManager.setDefaultMaxPerRoute(100)
    httpClient.setParams(httpParams)

    httpClient
  }

  private def configureDeadConnectionCleaner() = Executors.newScheduledThreadPool(1).scheduleAtFixedRate(new Runnable {
    override def run = {
      connectionManager.closeExpiredConnections
      connectionManager.closeIdleConnections(10, SECONDS)
    }
  }, 10, 10, SECONDS)
}
