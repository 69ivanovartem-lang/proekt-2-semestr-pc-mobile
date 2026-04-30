package com.example.pzovv

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pzovv.databinding.ActivityMainBinding
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.integration.android.IntentResult
import com.journeyapps.barcodescanner.CaptureActivity
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var apiService: ApiService
    private var currentOrderNumber: String = ""
    private val serverUrl = "http://192.168.143.208:8000"
    private val orderStatuses = arrayOf("В пути", "Собирается", "Прибыл")
    private val TAG = "PZV_MAIN"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.d(TAG, "App started")

        val retrofit = Retrofit.Builder()
            .baseUrl(serverUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(ApiService::class.java)

        binding.btnScanQr.setOnClickListener { startScan() }
        binding.btnChangeStatus.setOnClickListener { showStatusDialog() }
        binding.btnRefresh.setOnClickListener { loadOrders() }
        binding.btnAddTestOrder.setOnClickListener { createTestOrder() }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        loadOrders()
    }

    private fun startScan() {
        Log.d(TAG, "Starting scanner...")
        val integrator = IntentIntegrator(this)
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
        integrator.setPrompt("Наведите камеру на QR-код")
        integrator.setCameraId(0)
        integrator.setBeepEnabled(true)
        integrator.setOrientationLocked(true)
        integrator.setCaptureActivity(CaptureActivity::class.java)
        integrator.initiateScan()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(TAG, "Scan result received. Code: $resultCode")

        val result: IntentResult? = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            if (result.contents == null) {
                Log.d(TAG, "Scan cancelled")
                Toast.makeText(this, "Сканирование отменено", Toast.LENGTH_SHORT).show()
            } else {
                Log.d(TAG, "QR Content: ${result.contents}")
                Toast.makeText(this, "✅ QR считан!", Toast.LENGTH_SHORT).show()
                parseQRCode(result.contents)
            }
        } else {
            Log.d(TAG, "Scanner returned null")
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun parseQRCode(content: String) {
        Log.d(TAG, "Parsing: $content")
        val parts = content.split("|")
        val orderNumber = if (parts.size > 1 && parts[0] == "ORDER") parts[1] else parts[0]

        if (orderNumber.isNotEmpty()) {
            currentOrderNumber = orderNumber
            findAndShowOrder(orderNumber)
        } else {
            Toast.makeText(this, "❌ Неверный формат QR", Toast.LENGTH_SHORT).show()
        }
    }

    private fun findAndShowOrder(orderNumber: String) {
        Log.d(TAG, "Searching order: $orderNumber")
        lifecycleScope.launch {
            try {
                binding.tvStatus.text = "Поиск..."
                val orders = apiService.getOrders()
                Log.d(TAG, "Server returned ${orders.size} orders")

                val order = orders.find { it.orderNumber == orderNumber }
                if (order != null) {
                    Log.d(TAG, "Order found! Showing delivery confirmation.")
                    showDeliveryConfirmation(order)
                } else {
                    Toast.makeText(this@MainActivity, "❌ Заказ не найден", Toast.LENGTH_LONG).show()
                    binding.tvStatus.text = "Не найден"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Network error", e)
                Toast.makeText(this@MainActivity, "❌ Ошибка сети: ${e.message}", Toast.LENGTH_LONG).show()
                binding.tvStatus.text = "Ошибка"
            }
        }
    }

    private fun showDeliveryConfirmation(order: Order) {
        val message = StringBuilder()
        message.append("📦 Заказ №${order.orderNumber}\n\n")
        message.append("👤 Клиент: ${order.clientName}\n")
        message.append("📞 Телефон: ${order.phoneNumber}\n")
        message.append("\n📌 Статус: ${order.status}\n\n")
        message.append("📦 Товары:\n")
        order.items.forEach { item ->
            message.append("  • ${item.name} (x${item.quantity})\n")
        }

        if (order.status != "Прибыл") {
            message.append("\n⚠️ НЕЛЬЗЯ ВЫДАТЬ!\n")
            message.append("Заказ должен иметь статус 'Прибыл'\n")
            message.append("Текущий статус: ${order.status}")

            AlertDialog.Builder(this)
                .setTitle("📦 Информация о заказе")
                .setMessage(message.toString())
                .setPositiveButton("OK", null)
                .setNeutralButton("📝 Изменить статус") { _, _ ->
                    currentOrderNumber = order.orderNumber
                    showStatusDialog()
                }
                .show()
        } else {
            message.append("\n✅✅✅ Заказ прибыл и готов к выдаче! ✅✅✅\n\n")
            message.append("Подтвердить выдачу заказа клиенту?")

            AlertDialog.Builder(this)
                .setTitle("📦 ВЫДАЧА ЗАКАЗА")
                .setMessage(message.toString())
                .setPositiveButton("✅ ДА, ВЫДАТЬ") { _, _ ->
                    confirmDelivery(order.orderNumber)
                }
                .setNegativeButton("❌ ОТМЕНА", null)
                .show()
        }
    }

    private fun showStatusDialog() {
        if (currentOrderNumber.isEmpty()) {
            Toast.makeText(this, "⚠️ Сначала отсканируйте QR", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("📝 Статус для $currentOrderNumber")
            .setItems(orderStatuses) { _, which ->
                updateOrderStatus(currentOrderNumber, orderStatuses[which])
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun updateOrderStatus(orderNumber: String, newStatus: String) {
        lifecycleScope.launch {
            try {
                binding.tvStatus.text = "Сохранение..."
                val data = mapOf("order_number" to orderNumber, "status" to newStatus)
                apiService.updateStatus(body = data)
                Toast.makeText(this@MainActivity, "✅ Статус изменен на $newStatus", Toast.LENGTH_SHORT).show()
                currentOrderNumber = ""
                loadOrders()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "❌ Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun confirmDelivery(orderNumber: String) {
        lifecycleScope.launch {
            try {
                binding.tvStatus.text = "Выдача..."
                val deliveryData = mapOf("order_number" to orderNumber)  // ✅ ПРАВИЛЬНО
                val response = apiService.confirmDelivery(deliveryData)

                if (response["status"] == "success") {
                    Toast.makeText(this@MainActivity, "✅✅✅ ЗАКАЗ ВЫДАН! ✅✅✅", Toast.LENGTH_LONG).show()
                    binding.tvStatus.text = "✅ Заказ выдан"
                    currentOrderNumber = ""
                    loadOrders()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "❌ Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun createTestOrder() {
        lifecycleScope.launch {
            try {
                binding.tvStatus.text = "Создание..."
                val randomNum = (1000..9999).random()
                val testOrder = OrderRequest(
                    orderNumber = "TEST-$randomNum",
                    clientName = "Иван Тестовый",
                    phoneNumber = "89001234567",
                    items = listOf(
                        OrderItem("Кроссовки Nike", 1),
                        OrderItem("Носки", 3)
                    )
                )
                apiService.addOrder(testOrder)
                Toast.makeText(this@MainActivity, "✅ Создан: ${testOrder.orderNumber}", Toast.LENGTH_LONG).show()
                binding.tvStatus.text = "Заказ создан"
                loadOrders()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "❌ ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadOrders() {
        lifecycleScope.launch {
            try {
                binding.tvStatus.text = "Загрузка..."
                val orders = apiService.getOrders()
                binding.tvStatus.text = "✅ ${orders.size} заказов"
                binding.recyclerView.adapter = OrdersAdapter(orders)
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "❌ ${e.message}", Toast.LENGTH_LONG).show()
                binding.tvStatus.text = "Ошибка"
            }
        }
    }

    inner class OrdersAdapter(private val orders: List<Order>) : RecyclerView.Adapter<OrdersAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvNum: TextView = view.findViewById(android.R.id.text1)
            val tvInfo: TextView = view.findViewById(android.R.id.text2)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val order = orders[position]
            holder.tvNum.text = "№${order.orderNumber}"
            holder.tvInfo.text = "${order.clientName}\n${order.status}"
            holder.tvNum.setTextColor(
                when (order.status) {
                    "Выдан" -> Color.RED
                    "Прибыл" -> Color.parseColor("#4CAF50")
                    else -> Color.parseColor("#FF9800")
                }
            )
        }

        override fun getItemCount() = orders.size
    }
}