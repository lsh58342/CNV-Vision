package com.example.cnv.factory.model

/**
 * Building under a Factory (e.g. WA1, WA2).
 */
data class Building(
    val id: String,
    val factoryId: String,
    val name: String,
)
