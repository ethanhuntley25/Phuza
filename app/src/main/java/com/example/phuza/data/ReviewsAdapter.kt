package com.example.phuza.data

import com.google.gson.*
import java.lang.reflect.Type

class ReviewsAdapter : JsonDeserializer<List<Review>> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type,
        ctx: JsonDeserializationContext
    ): List<Review> {
        if (json == null || json.isJsonNull) return emptyList()
        // If server sends a proper array -> parse it
        if (json.isJsonArray) {
            return json.asJsonArray.mapNotNull { el ->
                try { ctx.deserialize<Review>(el, Review::class.java) } catch (_: Exception) { null }
            }
        }
        return emptyList()
    }
}
