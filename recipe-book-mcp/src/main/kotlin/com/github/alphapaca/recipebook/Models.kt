package com.github.alphapaca.recipebook

import kotlinx.serialization.Serializable

@Serializable
data class Recipe(
    val id: Long,
    val name: String,
    val ingredients: List<Ingredient>,
    val steps: List<String>,
    val links: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class Ingredient(
    val name: String,
    val amount: String,
)
