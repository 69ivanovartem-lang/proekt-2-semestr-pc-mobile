package com.example.mobileclient2

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.mobileclient2.databinding.ActivityMainBinding
import com.google.gson.annotations.SerializedName
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var apiService: ClientApiService
    private val serverUrl = "http://192.168.143.208:8000"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val retrofit = Retrofit.Builder()
            .baseUrl(serverUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(ClientApiService::class.java)

        binding.btnShowQR.setOnClickListener {
            val orderNumber = binding.etOrderNumber.text.toString().trim()
            if (orderNumber.isNotEmpty()) {
                loadOrderAndShowQR(orderNumber)
            } else {
                Toast.makeText(this, "Введите номер заказа", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadOrderAndShowQR(orderNumber: String) {
        lifecycleScope.launch {
            try {
                val orders = apiService.getOrders()
                val order = orders.find { it.orderNumber == orderNumber }

                if (order != null) {
                    binding.tvOrderNumber.text = "Заказ №${order.orderNumber}"
                    binding.tvClientName.text = "Клиент: ${order.clientName}"
                    binding.tvPhone.text = "Телефон: ${order.phoneNumber}"
                    binding.tvPZV.text = "ПВЗ: ${order.pzvNumber}"
                    binding.tvStatus.text = "Статус: ${order.status}"

                    val statusColor = when (order.status) {
                        "Выдан" -> Color.RED
                        "Прибыл" -> Color.GREEN
                        else -> Color.parseColor("#FF9800")
                    }
                    binding.tvStatus.setTextColor(statusColor)

                    val qrData = "ORDER|${order.orderNumber}|${order.clientName}|${order.phoneNumber}|${order.pzvNumber}"
                    val qrBitmap = generateQRCode(qrData)

                    binding.ivQRCode.setImageBitmap(qrBitmap)
                    binding.ivQRCode.visibility = View.VISIBLE
                    binding.cardOrder.visibility = View.VISIBLE
                    binding.tvMessage.visibility = View.VISIBLE

                    Toast.makeText(this@MainActivity, "Заказ найден!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "Заказ не найден", Toast.LENGTH_LONG).show()
                    clearDisplay()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                clearDisplay()
            }
        }
    }

    private fun generateQRCode(content: String, size: Int = 250): Bitmap {
        val bitMatrix: BitMatrix = MultiFormatWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            size,
            size
        )

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    private fun clearDisplay() {
        binding.ivQRCode.visibility = View.GONE
        binding.cardOrder.visibility = View.GONE
        binding.tvMessage.visibility = View.GONE
    }
}

interface ClientApiService {
    @GET("/orders")
    suspend fun getOrders(): List<Order>
}

data class Order(
    @SerializedName("pzv_number") val pzvNumber: String,
    @SerializedName("order_number") val orderNumber: String,
    @SerializedName("client_name") val clientName: String,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("status") val status: String
)