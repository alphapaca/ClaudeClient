package com.github.alphapaca.recipebook

import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

fun main(): Unit = runBlocking {
    val recipeService = RecipeService()

    val server = Server(
        serverInfo = Implementation(
            name = "recipe-book-mcp",
            version = "1.0.0"
        ),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = true),
            )
        )
    )

    // Tool: add_recipe
    server.addTool(
        name = "add_recipe",
        description = "Create a new recipe with ingredients, steps, and optional links.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("name", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("Name of the dish"))
                })
                put("ingredients", buildJsonObject {
                    put("type", JsonPrimitive("array"))
                    put("description", JsonPrimitive("List of ingredients, each with 'name' and 'amount'"))
                    put("items", buildJsonObject {
                        put("type", JsonPrimitive("object"))
                        put("properties", buildJsonObject {
                            put("name", buildJsonObject {
                                put("type", JsonPrimitive("string"))
                            })
                            put("amount", buildJsonObject {
                                put("type", JsonPrimitive("string"))
                            })
                        })
                        put("required", JsonArray(listOf(JsonPrimitive("name"), JsonPrimitive("amount"))))
                    })
                })
                put("steps", buildJsonObject {
                    put("type", JsonPrimitive("array"))
                    put("description", JsonPrimitive("List of cooking steps"))
                    put("items", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                    })
                })
                put("links", buildJsonObject {
                    put("type", JsonPrimitive("array"))
                    put("description", JsonPrimitive("Optional list of reference links (YouTube, blogs, etc.)"))
                    put("items", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                    })
                })
            },
            required = listOf("name", "ingredients", "steps")
        )
    ) { request ->
        try {
            val name = request.arguments?.get("name")?.jsonPrimitive?.content
                ?: return@addTool errorResult("'name' is required")

            val ingredientsJson = request.arguments?.get("ingredients")?.jsonArray
                ?: return@addTool errorResult("'ingredients' is required")

            val ingredients = ingredientsJson.map { item ->
                val obj = item.jsonObject
                Ingredient(
                    name = obj["name"]?.jsonPrimitive?.content ?: "",
                    amount = obj["amount"]?.jsonPrimitive?.content ?: "",
                )
            }

            val stepsJson = request.arguments?.get("steps")?.jsonArray
                ?: return@addTool errorResult("'steps' is required")

            val steps = stepsJson.map { it.jsonPrimitive.content }

            val linksJson = request.arguments?.get("links")?.jsonArray
            val links = linksJson?.map { it.jsonPrimitive.content } ?: emptyList()

            val recipe = recipeService.createRecipe(name, ingredients, steps, links)

            CallToolResult(
                content = listOf(
                    TextContent(text = formatRecipe(recipe, "Recipe created successfully!"))
                )
            )
        } catch (e: Exception) {
            errorResult("Error creating recipe: ${e.message}")
        }
    }

    // Tool: get_recipe
    server.addTool(
        name = "get_recipe",
        description = "Get a recipe by its ID.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("id", buildJsonObject {
                    put("type", JsonPrimitive("integer"))
                    put("description", JsonPrimitive("The recipe ID"))
                })
            },
            required = listOf("id")
        )
    ) { request ->
        try {
            val id = request.arguments?.get("id")?.jsonPrimitive?.content?.toLongOrNull()
                ?: return@addTool errorResult("'id' is required and must be a number")

            val recipe = recipeService.getRecipe(id)
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent(text = "Recipe with ID $id not found."))
                )

            CallToolResult(
                content = listOf(TextContent(text = formatRecipe(recipe)))
            )
        } catch (e: Exception) {
            errorResult("Error getting recipe: ${e.message}")
        }
    }

    // Tool: list_recipes
    server.addTool(
        name = "list_recipes",
        description = "List all recipes, optionally filtering by name.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("query", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("Optional search query to filter by name"))
                })
                put("limit", buildJsonObject {
                    put("type", JsonPrimitive("integer"))
                    put("description", JsonPrimitive("Maximum number of results (default: 20)"))
                })
            },
            required = emptyList()
        )
    ) { request ->
        try {
            val query = request.arguments?.get("query")?.jsonPrimitive?.content
            val limit = request.arguments?.get("limit")?.let { (it as? JsonPrimitive)?.int }?.coerceIn(1, 100) ?: 20

            val recipes = recipeService.listRecipes(query, limit)

            if (recipes.isEmpty()) {
                CallToolResult(
                    content = listOf(
                        TextContent(
                            text = if (query != null) "No recipes found matching '$query'."
                            else "No recipes found. Use 'add_recipe' to create one."
                        )
                    )
                )
            } else {
                val text = buildString {
                    appendLine("Found ${recipes.size} recipe(s):")
                    appendLine()
                    recipes.forEachIndexed { index, recipe ->
                        appendLine("${index + 1}. [ID: ${recipe.id}] ${recipe.name}")
                        appendLine("   Ingredients: ${recipe.ingredients.size} | Steps: ${recipe.steps.size}")
                    }
                }
                CallToolResult(content = listOf(TextContent(text = text)))
            }
        } catch (e: Exception) {
            errorResult("Error listing recipes: ${e.message}")
        }
    }

    // Tool: update_recipe
    server.addTool(
        name = "update_recipe",
        description = "Update an existing recipe. Only provided fields will be updated.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("id", buildJsonObject {
                    put("type", JsonPrimitive("integer"))
                    put("description", JsonPrimitive("The recipe ID to update"))
                })
                put("name", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("New name for the dish"))
                })
                put("ingredients", buildJsonObject {
                    put("type", JsonPrimitive("array"))
                    put("description", JsonPrimitive("New list of ingredients"))
                    put("items", buildJsonObject {
                        put("type", JsonPrimitive("object"))
                        put("properties", buildJsonObject {
                            put("name", buildJsonObject {
                                put("type", JsonPrimitive("string"))
                            })
                            put("amount", buildJsonObject {
                                put("type", JsonPrimitive("string"))
                            })
                        })
                    })
                })
                put("steps", buildJsonObject {
                    put("type", JsonPrimitive("array"))
                    put("description", JsonPrimitive("New list of cooking steps"))
                    put("items", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                    })
                })
                put("links", buildJsonObject {
                    put("type", JsonPrimitive("array"))
                    put("description", JsonPrimitive("New list of reference links"))
                    put("items", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                    })
                })
            },
            required = listOf("id")
        )
    ) { request ->
        try {
            val id = request.arguments?.get("id")?.jsonPrimitive?.content?.toLongOrNull()
                ?: return@addTool errorResult("'id' is required and must be a number")

            val name = request.arguments?.get("name")?.jsonPrimitive?.content

            val ingredientsJson = request.arguments?.get("ingredients")?.jsonArray
            val ingredients = ingredientsJson?.map { item ->
                val obj = item.jsonObject
                Ingredient(
                    name = obj["name"]?.jsonPrimitive?.content ?: "",
                    amount = obj["amount"]?.jsonPrimitive?.content ?: "",
                )
            }

            val stepsJson = request.arguments?.get("steps")?.jsonArray
            val steps = stepsJson?.map { it.jsonPrimitive.content }

            val linksJson = request.arguments?.get("links")?.jsonArray
            val links = linksJson?.map { it.jsonPrimitive.content }

            val recipe = recipeService.updateRecipe(id, name, ingredients, steps, links)
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent(text = "Recipe with ID $id not found."))
                )

            CallToolResult(
                content = listOf(TextContent(text = formatRecipe(recipe, "Recipe updated successfully!")))
            )
        } catch (e: Exception) {
            errorResult("Error updating recipe: ${e.message}")
        }
    }

    // Tool: delete_recipe
    server.addTool(
        name = "delete_recipe",
        description = "Delete a recipe by its ID.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("id", buildJsonObject {
                    put("type", JsonPrimitive("integer"))
                    put("description", JsonPrimitive("The recipe ID to delete"))
                })
            },
            required = listOf("id")
        )
    ) { request ->
        try {
            val id = request.arguments?.get("id")?.jsonPrimitive?.content?.toLongOrNull()
                ?: return@addTool errorResult("'id' is required and must be a number")

            val deleted = recipeService.deleteRecipe(id)

            CallToolResult(
                content = listOf(
                    TextContent(
                        text = if (deleted) "Recipe $id deleted successfully."
                        else "Recipe $id not found."
                    )
                )
            )
        } catch (e: Exception) {
            errorResult("Error deleting recipe: ${e.message}")
        }
    }

    val transport = StdioServerTransport(
        inputStream = System.`in`.asSource().buffered(),
        outputStream = System.out.asSink().buffered()
    )

    val done = Job()
    server.onClose {
        recipeService.close()
        done.complete()
    }

    server.connect(transport)
    done.join()
}

private fun errorResult(message: String) = CallToolResult(
    content = listOf(TextContent(text = "Error: $message")),
    isError = true
)

private fun formatRecipe(recipe: Recipe, header: String? = null): String = buildString {
    if (header != null) {
        appendLine(header)
        appendLine()
    }
    appendLine("=== ${recipe.name} ===")
    appendLine("ID: ${recipe.id}")
    appendLine()
    appendLine("INGREDIENTS:")
    recipe.ingredients.forEachIndexed { index, ing ->
        appendLine("  ${index + 1}. ${ing.name}: ${ing.amount}")
    }
    appendLine()
    appendLine("STEPS:")
    recipe.steps.forEachIndexed { index, step ->
        appendLine("  ${index + 1}. $step")
    }
    if (recipe.links.isNotEmpty()) {
        appendLine()
        appendLine("LINKS:")
        recipe.links.forEach { link ->
            appendLine("  - $link")
        }
    }
}
