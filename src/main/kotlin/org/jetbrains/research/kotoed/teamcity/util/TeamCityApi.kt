package org.jetbrains.research.kotoed.teamcity.util

import org.jetbrains.research.kotoed.config.Config

data class ApiEndpoint(val path: String) {
    operator fun invoke(): String = path
}

interface Locator

object EmptyLocator : Locator {
    override fun toString(): String = ""
}

data class StringLocator(val data: String) : Locator {
    override fun toString(): String = "$data"
}

data class DimensionLocator(val dimension: String, val value: String) : Locator {
    override fun toString(): String = "$dimension:$value"

    companion object {
        fun from(dimension: String, value: String?) =
                if (value == null) EmptyLocator else DimensionLocator(dimension, value)

        fun from(dimension: String, value: Int?) =
                if (value == null) EmptyLocator else DimensionLocator(dimension, value.toString())
    }
}

operator infix fun ApiEndpoint.plus(locator: Locator) = ApiEndpoint("$path/$locator")()

operator infix fun Locator.times(locator: Locator) =
        if (this is EmptyLocator) locator
        else if (locator is EmptyLocator) this
        else StringLocator("$this,$locator")

operator infix fun Locator.div(locator: Locator) =
        if (this is EmptyLocator) locator
        else if (locator is EmptyLocator) this
        else StringLocator("$this/$locator")

fun Locator.all() =
        if (this is EmptyLocator) this else StringLocator("?locator=$this")

object TeamCityApi {
    private val endpointRoot = Config.TeamCity.EndpointRoot

    private operator fun String.unaryPlus() = ApiEndpoint("$endpointRoot/$this")
    private operator fun String.unaryMinus() = StringLocator(this)

    val Projects = +"projects"
    val VcsRoots = +"vcs-roots"
    val BuildTypes = +"buildTypes"
    val BuildQueue = +"buildQueue"
    val Builds = +"builds"
}