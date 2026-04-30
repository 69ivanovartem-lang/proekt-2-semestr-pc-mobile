package com.example.pzovv

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.pzovv.databinding.ActivityMainBinding
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.integration.android.IntentResult
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var serverUrl = "http://192.168.143.208:8000"
    private lateinit var apiService: ApiService
    private var currentOrderNumber: String = ""

    // Статусы заказов
    private val orderStatuses = arrayOf("В пути", "Собирается", "Прибыл")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        updateRetrofitInstance()

        binding.btnScanQr.setOnClickListener {
            scanQR()
        }

        binding.btnCreateOrder.setOnClickListener {
            createOrder()
        }

        binding.btnRefresh.setOnClickListener {
            loadOrders()
        }

        // Кнопка изменения статуса
        binding.btnChangeStatus.setOnClickListener {
            showStatusDialog()
        }
    }

    private fun updateRetrofitInstance() {
        val retrofit = Retrofit.Builder()
            .baseUrl(serverUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(ApiService::class.java)
        binding.tvStatus.text = "📱 Готов к работе"
    }

    private fun scanQR() {
        val integrator = IntentIntegrator(this)
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
        integrator.setPrompt("Отсканируйте QR-код заказа")
        integrator.setCameraId(0)
        integrator.setBeepEnabled(true)
        integrator.initiateScan()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result: IntentResult? = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            if (result.contents == null) {
                Toast.makeText(this, "Сканирование отменено", Toast.LENGTH_SHORT).show()
            } else {
                parseQRCode(result.contents!!)
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun parseQRCode(content: String) {
        try {
            val parts = content.split("|")
            if (parts.size >= 5 && parts[0] == "ORDER") {
                val orderNumber = parts[1]
                val clientName = parts[2]
                val phone = parts[3]
                val pzv = parts[4]

                currentOrderNumber = orderNumber

                // Сначала получаем актуальный статус заказа
                lifecycleScope.launch {
                    try {
                        val orders = apiService.getOrders()
                        val order = orders.find { it.orderNumber == orderNumber }

                        if (order != null) {
                            showDeliveryConfirmation(orderNumber, clientName, phone, pzv, order.status)
                        } else {
                            Toast.makeText(this@MainActivity, "❌ Заказ не найден", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "❌ Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "❌ Неверный формат QR", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "❌ Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showStatusDialog() {
        AlertDialog.Builder(this)
            .setTitle("📦 Изменение статуса")
            .setItems(orderStatuses) { _, which ->
                val newStatus = orderStatuses[which]
                updateOrderStatus(currentOrderNumber, newStatus)
            }
            .show()
    }

    private fun updateOrderStatus(orderNumber: String, newStatus: String) {
        lifecycleScope.launch {
            try {
                binding.tvStatus.text = "Изменение статуса..."

                // Создаем объект StatusUpdate
                val statusData = StatusUpdate(
                    orderNumber = orderNumber,
                    status = newStatus
                )

                // Вызываем API
                val response = apiService.updateStatus(statusData)

                Toast.makeText(this@MainActivity, "✅ Статус: $newStatus", Toast.LENGTH_SHORT).show()
                binding.tvStatus.text = "Статус изменён"
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Неизвестная ошибка"
                Toast.makeText(this@MainActivity, "❌ Ошибка: $errorMsg", Toast.LENGTH_LONG).show()
                binding.tvStatus.text = "❌ Ошибка"
                e.printStackTrace()
            }
        }
    }

    private fun showDeliveryConfirmation(orderNumber: String, clientName: String, phone: String, pzv: String, currentStatus: String) {
        val message = StringBuilder()
        message.append("Заказ №$orderNumber\n\n")
        message.append("Клиент: $clientName\n")
        message.append("Телефон: $phone\n")
        message.append("ПВЗ: $pzv\n")
        message.append("\n📌 Текущий статус: $currentStatus\n\n")

        if (currentStatus != "Прибыл") {
            message.append("⚠️ НЕЛЬЗЯ ВЫДАТЬ!\n")
            message.append("Заказ должен иметь статус 'Прибыл'\n")
            message.append("\nСначала измените статус на 'Прибыл'")

            AlertDialog.Builder(this)
                .setTitle("📦 Информация о заказе")
                .setMessage(message.toString())
                .setPositiveButton("OK", null)
                .setNeutralButton("📝 Изменить статус") { _, _ ->
                    updateOrderStatus(orderNumber, "Прибыл")
                }
                .setCancelable(false)
                .show()
        } else {
            message.append("✅ Заказ прибыл и готов к выдаче!\n\n")
            message.append("Подтвердить выдачу?")

            AlertDialog.Builder(this)
                .setTitle("📦 Выдача заказа")
                .setMessage(message.toString())
                .setPositiveButton("✅ ДА, ВЫДАТЬ") { _, _ ->
                    confirmDelivery(orderNumber, true)
                }
                .setNegativeButton("❌ ОТМЕНА", null)
                .setCancelable(false)
                .show()
        }
    }

    private fun confirmDelivery(orderNumber: String, confirmed: Boolean) {
        lifecycleScope.launch {
            try {
                binding.tvStatus.text = "Обработка..."
                val response = apiService.confirmDelivery(DeliveryConfirm(orderNumber, confirmed))

                val msg = response["message"] ?: "OK"

                if (response["status"] == "success") {
                    Toast.makeText(this@MainActivity, "✅ Заказ ВЫДАН!", Toast.LENGTH_LONG).show()
                    binding.tvStatus.text = "✅ Заказ выдан"
                } else {
                    Toast.makeText(this@MainActivity, "⚠️ $msg", Toast.LENGTH_SHORT).show()
                    binding.tvStatus.text = "❌ Отменено"
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Неизвестная ошибка"
                Toast.makeText(this@MainActivity, "❌ $errorMsg", Toast.LENGTH_LONG).show()
                binding.tvStatus.text = "❌ Ошибка"
            }
        }
    }

    private fun createOrder() {
        val pzv = binding.etPzv.text.toString()
        val orderNum = binding.etOrderNum.text.toString()
        val client = binding.etClient.text.toString()
        val phone = binding.etPhone.text.toString()

        if (pzv.isEmpty() || orderNum.isEmpty() || client.isEmpty()) {
            Toast.makeText(this, "⚠️ Заполните все поля!", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                binding.tvStatus.text = "Создание..."
                val order = OrderRequest(pzv, orderNum, client, phone)
                apiService.addOrder(order)

                Toast.makeText(this@MainActivity, "✅ Заказ создан!", Toast.LENGTH_SHORT).show()
                binding.tvStatus.text = "✅ Создан"

                binding.etOrderNum.text.clear()
                binding.etClient.text.clear()
                binding.etPhone.text.clear()
            } catch (e: Exception) {
                if (e.message?.contains("400") == true) {
                    Toast.makeText(this@MainActivity, "⚠️ Заказ уже существует", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "❌ Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                }
                binding.tvStatus.text = "❌ Ошибка"
            }
        }
    }

    private fun loadOrders() {
        lifecycleScope.launch {
            try {
                binding.tvStatus.text = "Загрузка..."
                val orders = apiService.getOrders()
                binding.tvStatus.text = "✅ Загружено: ${orders.size}"
                Toast.makeText(this@MainActivity, "Заказов: ${orders.size}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "❌ Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                binding.tvStatus.text = "❌ Ошибка соединения"
            }
        }
    }
}