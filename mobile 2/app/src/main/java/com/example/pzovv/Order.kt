package com.example.pzovv

import com.google.gson.annotations.SerializedName

data class Order(
    @SerializedName("pzv_number") val pzvNumber: String,
    @SerializedName("order_number") val orderNumber: String,
    @SerializedName("client_name") val clientName: String,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("status") val status: String = "В пути",
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("delivered_at") val deliveredAt: String? = null
)

data class OrderRequest(
    @SerializedName("pzv_number") val pzvNumber: String,
    @SerializedName("order_number") val orderNumber: String,
    @SerializedName("client_name") val clientName: String,
    @SerializedName("phone_number") val phoneNumber: String
)

data class StatusUpdate(
    @SerializedName("order_number") val orderNumber: String,
    @SerializedName("status") val status: String
)

data class DeliveryConfirm(
    @SerializedName("order_number") val orderNumber: String,
    @SerializedName("confirmed") val confirmed: Boolean
)