package de.mm20.launcher2.plugin.metoffice.api

import kotlinx.serialization.Serializable

@Serializable
data class MetGeo(
    val name: String?,
    val area: String?,
    val latLong: List<Double>?,
    val country: String?,
)