package com.example.cnv.factory.model

/**
 * Floor under a Building (e.g. 1F, 2F).
 */
data class Floor(
    val id: String,
    val buildingId: String,
    val name: String,
)
