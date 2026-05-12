package com.example.myapplication.models

import com.google.gson.annotations.SerializedName
import com.google.firebase.database.Exclude
import com.google.firebase.database.PropertyName

data class RegisterRequest(
    val username: String,
    val name: String,
    val email: String,
    val password: String,
    val role: String,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("area_id") val areaId: Int? = null,
    @SerializedName("purok") val purok: String? = null,
    @SerializedName("complete_address") val completeAddress: String? = null,
    @SerializedName("license_number") val licenseNumber: String? = null,
    @SerializedName("preferred_truck") val preferredTruck: String? = null
)

data class LoginRequest(
    val username_or_email: String,
    val password: String
)

data class ApiResponse(
    val success: Boolean,
    val message: String?,
    val user: UserData? = null
)

data class UserData(
    @SerializedName("user_id") val userId: Int = 0,
    val username: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "",
    val phone: String? = null,
    val purok: String? = null,
    val password: String? = null,
    @get:PropertyName("complete_address") @set:PropertyName("complete_address")
    @SerializedName("complete_address") var completeAddress: String? = null,
    @get:PropertyName("license_number") @set:PropertyName("license_number")
    @SerializedName("license_number") var licenseNumber: String? = null,
    @get:PropertyName("preferred_truck") @set:PropertyName("preferred_truck")
    @SerializedName("preferred_truck") var preferredTruck: String? = null,
    @get:Exclude @set:Exclude var requestId: String? = null
)

data class UsersResponse(
    val success: Boolean,
    val message: String?,
    val residents: List<UserData>?,
    val users: List<UserData>?
)
