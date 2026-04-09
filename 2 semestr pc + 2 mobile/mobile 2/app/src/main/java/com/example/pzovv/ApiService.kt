package com.example.pzovv

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ApiService {
    @GET("/orders")
    suspend fun getOrders(): List<Order>

    @POST("/add")
    suspend fun addOrder(@Body order: OrderRequest): Map<String, String>

    @POST("/update-status")
    suspend fun updateStatus(@Body data: StatusUpdate): Map<String, String>

    @POST("/confirm-delivery")
    suspend fun confirmDelivery(@Body data: DeliveryConfirm): Map<String, String>
}