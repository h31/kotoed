package org.jetbrains.research.kotoed.integration

import com.sun.jersey.api.client.Client
import com.sun.jersey.api.client.config.DefaultClientConfig
import io.vertx.core.Vertx
import kotlinx.coroutines.experimental.future.future
import kotlinx.coroutines.experimental.newSingleThreadContext
import org.jetbrains.research.kotoed.config.Config
import org.jetbrains.research.kotoed.startApplication
import java.util.concurrent.Future
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.UriBuilder

fun startServer(): Future<Vertx> {
    System.setProperty("kotoed.settingsFile", "testenvSettings.json")
    val stc = newSingleThreadContext("kotoed.testing.tc")
    return future(stc) { startApplication() } // FIXME: how to wait for a coroutine in a better way?
}

fun stopServer(vertx: Future<Vertx>) {
    vertx.get().close()
}

fun wget(path: String, mediaType: MediaType = MediaType.APPLICATION_JSON_TYPE,
         params: Iterable<Pair<String, Any?>> = listOf()): String {
    val config = DefaultClientConfig()
    val client = Client.create(config)
    val resource = client.resource(UriBuilder.fromUri("http://localhost:${Config.Root.Port}").build())

    return resource.path(path).let {
        params.fold(it) { res, (k, v) -> res.queryParam(k, "$v") }
    }.accept(mediaType).get(String::class.java)
}

fun wpost(path: String, mediaType: MediaType = MediaType.APPLICATION_JSON_TYPE, payload: String = ""): String {
    val config = DefaultClientConfig()
    val client = Client.create(config)
    val resource = client.resource(UriBuilder.fromUri("http://localhost:${Config.Root.Port}").build())

    return resource.path(path).accept(mediaType).post(String::class.java, payload)
}
