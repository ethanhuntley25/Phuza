package com.example.phuza.data

import com.google.gson.annotations.SerializedName
import com.google.gson.annotations.JsonAdapter

data class UserDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("uid") val uid: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("firstName") val firstName: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("avatar") val avatar: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("location") val location: String? = null,
    @SerializedName("favoriteDrink") val favoriteDrink: String? = null,
    @SerializedName("createdAt") val createdAt: Long? = null,
    @SerializedName("dateofbirth") val dateOfBirth: String? = null,

    @JsonAdapter(ReviewsAdapter::class)
    @SerializedName("reviews") val reviews: List<Review> = emptyList()
)
