package com.github.alphapaca.recipebook

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.DriverManager

class RecipeService {
    private val logger = LoggerFactory.getLogger(RecipeService::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    private val connection: Connection by lazy {
        val dbPath = System.getProperty("user.home") + "/.recipe-book-mcp/recipes.db"
        java.io.File(dbPath).parentFile?.mkdirs()
        DriverManager.getConnection("jdbc:sqlite:$dbPath").also {
            initDatabase(it)
        }
    }

    private fun initDatabase(conn: Connection) {
        conn.createStatement().use { stmt ->
            // Enable foreign keys
            stmt.execute("PRAGMA foreign_keys = ON")

            // Create recipes table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS recipes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    steps_json TEXT NOT NULL,
                    links_json TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
            """)

            // Create ingredients table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS ingredients (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    recipe_id INTEGER NOT NULL,
                    name TEXT NOT NULL,
                    amount TEXT NOT NULL,
                    FOREIGN KEY (recipe_id) REFERENCES recipes(id) ON DELETE CASCADE
                )
            """)

            // Create indexes
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_recipe_name ON recipes(name)")
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_ingredients_recipe ON ingredients(recipe_id)")
        }
        logger.info("Recipe database initialized")
    }

    fun createRecipe(
        name: String,
        ingredients: List<Ingredient>,
        steps: List<String>,
        links: List<String> = emptyList(),
    ): Recipe {
        val now = System.currentTimeMillis()
        val stepsJson = json.encodeToString(steps)
        val linksJson = json.encodeToString(links)

        val sql = "INSERT INTO recipes (name, steps_json, links_json, created_at, updated_at) VALUES (?, ?, ?, ?, ?)"
        connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS).use { stmt ->
            stmt.setString(1, name)
            stmt.setString(2, stepsJson)
            stmt.setString(3, linksJson)
            stmt.setLong(4, now)
            stmt.setLong(5, now)
            stmt.executeUpdate()

            val rs = stmt.generatedKeys
            rs.next()
            val recipeId = rs.getLong(1)

            // Insert ingredients
            insertIngredients(recipeId, ingredients)

            logger.info("Created recipe $recipeId: '$name'")

            return Recipe(
                id = recipeId,
                name = name,
                ingredients = ingredients,
                steps = steps,
                links = links,
                createdAt = now,
                updatedAt = now,
            )
        }
    }

    private fun insertIngredients(recipeId: Long, ingredients: List<Ingredient>) {
        val sql = "INSERT INTO ingredients (recipe_id, name, amount) VALUES (?, ?, ?)"
        connection.prepareStatement(sql).use { stmt ->
            for (ingredient in ingredients) {
                stmt.setLong(1, recipeId)
                stmt.setString(2, ingredient.name)
                stmt.setString(3, ingredient.amount)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    private fun getIngredientsForRecipe(recipeId: Long): List<Ingredient> {
        val sql = "SELECT name, amount FROM ingredients WHERE recipe_id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, recipeId)
            val rs = stmt.executeQuery()
            val ingredients = mutableListOf<Ingredient>()
            while (rs.next()) {
                ingredients.add(
                    Ingredient(
                        name = rs.getString("name"),
                        amount = rs.getString("amount"),
                    )
                )
            }
            return ingredients
        }
    }

    fun getRecipe(id: Long): Recipe? {
        val sql = "SELECT id, name, steps_json, links_json, created_at, updated_at FROM recipes WHERE id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, id)
            val rs = stmt.executeQuery()
            if (!rs.next()) return null

            val recipeId = rs.getLong("id")
            return Recipe(
                id = recipeId,
                name = rs.getString("name"),
                ingredients = getIngredientsForRecipe(recipeId),
                steps = json.decodeFromString<List<String>>(rs.getString("steps_json")),
                links = json.decodeFromString<List<String>>(rs.getString("links_json")),
                createdAt = rs.getLong("created_at"),
                updatedAt = rs.getLong("updated_at"),
            )
        }
    }

    fun listRecipes(query: String? = null, limit: Int = 20): List<Recipe> {
        val sql = if (query.isNullOrBlank()) {
            "SELECT id, name, steps_json, links_json, created_at, updated_at FROM recipes ORDER BY updated_at DESC LIMIT ?"
        } else {
            "SELECT id, name, steps_json, links_json, created_at, updated_at FROM recipes WHERE name LIKE ? ORDER BY updated_at DESC LIMIT ?"
        }

        connection.prepareStatement(sql).use { stmt ->
            if (query.isNullOrBlank()) {
                stmt.setInt(1, limit)
            } else {
                stmt.setString(1, "%$query%")
                stmt.setInt(2, limit)
            }

            val rs = stmt.executeQuery()
            val recipes = mutableListOf<Recipe>()
            while (rs.next()) {
                val recipeId = rs.getLong("id")
                recipes.add(
                    Recipe(
                        id = recipeId,
                        name = rs.getString("name"),
                        ingredients = getIngredientsForRecipe(recipeId),
                        steps = json.decodeFromString<List<String>>(rs.getString("steps_json")),
                        links = json.decodeFromString<List<String>>(rs.getString("links_json")),
                        createdAt = rs.getLong("created_at"),
                        updatedAt = rs.getLong("updated_at"),
                    )
                )
            }
            return recipes
        }
    }

    fun updateRecipe(
        id: Long,
        name: String? = null,
        ingredients: List<Ingredient>? = null,
        steps: List<String>? = null,
        links: List<String>? = null,
    ): Recipe? {
        val existing = getRecipe(id) ?: return null

        val newName = name ?: existing.name
        val newIngredients = ingredients ?: existing.ingredients
        val newSteps = steps ?: existing.steps
        val newLinks = links ?: existing.links
        val now = System.currentTimeMillis()

        val sql = "UPDATE recipes SET name = ?, steps_json = ?, links_json = ?, updated_at = ? WHERE id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, newName)
            stmt.setString(2, json.encodeToString(newSteps))
            stmt.setString(3, json.encodeToString(newLinks))
            stmt.setLong(4, now)
            stmt.setLong(5, id)
            stmt.executeUpdate()
        }

        // Update ingredients if changed
        if (ingredients != null) {
            // Delete old ingredients
            connection.prepareStatement("DELETE FROM ingredients WHERE recipe_id = ?").use { stmt ->
                stmt.setLong(1, id)
                stmt.executeUpdate()
            }
            // Insert new ingredients
            insertIngredients(id, newIngredients)
        }

        logger.info("Updated recipe $id: '$newName'")

        return Recipe(
            id = id,
            name = newName,
            ingredients = newIngredients,
            steps = newSteps,
            links = newLinks,
            createdAt = existing.createdAt,
            updatedAt = now,
        )
    }

    fun deleteRecipe(id: Long): Boolean {
        val sql = "DELETE FROM recipes WHERE id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, id)
            val deleted = stmt.executeUpdate() > 0
            if (deleted) {
                logger.info("Deleted recipe $id")
            }
            return deleted
        }
    }

    fun close() {
        connection.close()
        logger.info("Recipe service closed")
    }
}
