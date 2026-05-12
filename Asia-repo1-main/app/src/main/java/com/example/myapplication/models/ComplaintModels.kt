package com.example.myapplication.models

import com.google.gson.annotations.SerializedName

data class Complaint(
    val id: Int,
    @SerializedName("user_id") val userId: Int,
    @SerializedName("full_name") val fullName: String?,
    val purok: String?,
    val category: String,
    val description: String,
    val status: String,
    @SerializedName("admin_response") val adminResponse: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String?
)

data class ComplaintsResponse(
    val success: Boolean,
    val message: String?,
    val data: List<Complaint>?
)

data class UpdateComplaintRequest(
    @SerializedName("complaint_id") val complaintId: Int,
    val status: String,
    @SerializedName("admin_response") val adminResponse: String?
)