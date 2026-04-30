package com.example.mobileclient2

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

data class OrderItem(
    @SerializedName("name") val name: String,
    @SerializedName("quantity") val quantity: Int
)

data class OrderRequest(
    @SerializedName("order_number") val orderNumber: String,
    @SerializedName("client_name") val clientName: String,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("items") val items: List<OrderItem>
)

data class Order(
    @SerializedName("order_number") val orderNumber: String,
    @SerializedName("client_name") val clientName: String,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("status") val status: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("items") val items: List<OrderItem>
)

interface ApiService {
    @GET("/client-orders/{phone}")
    suspend fun getOrdersByPhone(@Path("phone") phone: String): List<Order>

    @POST("/add")
    suspend fun addOrder(@Body order: OrderRequest): Map<String, String>
}