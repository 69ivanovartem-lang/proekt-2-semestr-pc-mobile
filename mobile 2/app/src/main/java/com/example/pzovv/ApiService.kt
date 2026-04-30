package com.example.pzovv

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST

data class OrderItem(
    @SerializedName("name") val name: String,
    @SerializedName("quantity") val quantity: Int
)

data class Order(
    @SerializedName("order_number") val orderNumber: String,
    @SerializedName("client_name") val clientName: String,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("status") val status: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("items") val items: List<OrderItem>
)

data class OrderRequest(
    @SerializedName("order_number") val orderNumber: String,
    @SerializedName("client_name") val clientName: String,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("items") val items: List<OrderItem>
)

data class StatusUpdate(
    @SerializedName("order_number") val orderNumber: String,
    @SerializedName("status") val status: String
)

data class DeliveryConfirm(
    @SerializedName("order_number") val orderNumber: String
)

interface ApiService {
    @GET("/orders")
    suspend fun getOrders(): List<Order>

    @POST("/add")
    suspend fun addOrder(@Body order: OrderRequest): Map<String, String>


    @POST("/update-status")
    suspend fun updateStatus(@Body body: Map<String, String>): Map<String, String>

    @POST("/confirm-delivery")
    suspend fun confirmDelivery(@Body data: Map<String, String>): Map<String, String>

    @DELETE("/orders/clear")
    suspend fun clearOrders(): Map<String, String>
}