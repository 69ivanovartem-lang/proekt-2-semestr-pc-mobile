package com.example.mobileclient2

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mobileclient2.databinding.ActivityMainBinding
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var apiService: ApiService
    private val serverUrl = "http://192.168.143.208:8000"

    // Список товаров в корзине
    private val cartItems = mutableListOf<CartItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)

            val retrofit = Retrofit.Builder()
                .baseUrl(serverUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            apiService = retrofit.create(ApiService::class.java)

            // Кнопки поиска
            binding.btnSearch.setOnClickListener {
                val phone = binding.etPhone.text.toString().trim()
                if (phone.isNotEmpty()) {
                    loadOrders(phone)
                } else {
                    Toast.makeText(this, "Введите номер телефона", Toast.LENGTH_SHORT).show()
                }
            }

            // Кнопка добавления товара
            binding.btnAddItem.setOnClickListener { addItemToCart() }

            // Кнопка создания заказа
            binding.btnCreateOrder.setOnClickListener { createOrder() }

            // Настройка RecyclerView для заказов
            binding.recyclerView.layoutManager = LinearLayoutManager(this)

            // Настройка RecyclerView для корзины
            binding.recyclerCart.layoutManager = LinearLayoutManager(this)
            binding.recyclerCart.adapter = CartAdapter()

        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun addItemToCart() {
        val itemName = binding.etItemName.text.toString().trim()
        val itemQty = binding.etItemQty.text.toString().trim()

        if (itemName.isEmpty()) {
            Toast.makeText(this, "Введите название товара", Toast.LENGTH_SHORT).show()
            return
        }

        val qty = itemQty.toIntOrNull() ?: 1
        if (qty <= 0) {
            Toast.makeText(this, "Количество должно быть больше 0", Toast.LENGTH_SHORT).show()
            return
        }

        cartItems.add(CartItem(itemName, qty))
        binding.recyclerCart.adapter?.notifyDataSetChanged()

        // Очистка полей
        binding.etItemName.text?.clear()
        binding.etItemQty.text?.clear()

        Toast.makeText(this, "✅ Товар добавлен", Toast.LENGTH_SHORT).show()
    }

    private fun createOrder() {
        val clientName = binding.etClientName.text.toString().trim()
        val clientPhone = binding.etClientPhone.text.toString().trim()

        if (clientName.isEmpty()) {
            Toast.makeText(this, "Введите ваше имя", Toast.LENGTH_SHORT).show()
            return
        }

        if (clientPhone.isEmpty()) {
            Toast.makeText(this, "Введите ваш телефон", Toast.LENGTH_SHORT).show()
            return
        }

        if (cartItems.isEmpty()) {
            Toast.makeText(this, "Добавьте хотя бы один товар", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                binding.tvStatus.text = "Создание заказа..."

                val orderRequest = OrderRequest(
                    orderNumber = "CLI-${(1000..9999).random()}",
                    clientName = clientName,
                    phoneNumber = clientPhone,
                    items = cartItems.map { OrderItem(it.name, it.quantity) }
                )

                val response = apiService.addOrder(orderRequest)

                if (response["status"].equals("success")) {
                    Toast.makeText(this@MainActivity, "✅ Заказ создан!", Toast.LENGTH_LONG).show()

                    binding.etClientName.text?.clear()
                    binding.etClientPhone.text?.clear()
                    cartItems.clear()
                    binding.recyclerCart.adapter?.notifyDataSetChanged()

                    binding.tvStatus.text = "Заказ создан"
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "❌ Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                binding.tvStatus.text = "Ошибка"
            }
        }
    }

    private fun loadOrders(phone: String) {
        lifecycleScope.launch {
            try {
                binding.tvStatus.visibility = View.VISIBLE
                binding.tvStatus.text = "Загрузка..."

                val orders = apiService.getOrdersByPhone(phone)

                if (orders.isEmpty()) {
                    binding.tvStatus.text = "Заказов не найдено"
                    binding.recyclerView.adapter = null
                } else {
                    binding.tvStatus.text = "Найдено заказов: ${orders.size}"
                    binding.recyclerView.adapter = OrdersAdapter(orders)
                }
            } catch (e: Exception) {
                binding.tvStatus.text = "Ошибка: ${e.message}"
                Toast.makeText(this@MainActivity, "Ошибка подключения: ${e.message}", Toast.LENGTH_LONG).show()
                binding.recyclerView.adapter = null
            }
        }
    }

    private fun generateQRCode(content: String, size: Int = 250): Bitmap {
        val bitMatrix: BitMatrix = MultiFormatWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            size,
            size,
            mapOf(com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8")
        )

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    private fun showQRDialog(order: Order) {
        try {
            val qrData = "ORDER|${order.orderNumber}|${order.clientName}|${order.phoneNumber}|${order.items.firstOrNull()?.name ?: ""}"
            val qrBitmap = generateQRCode(qrData)

            val imageView = ImageView(this).apply {
                setImageBitmap(qrBitmap)
                setPadding(20, 20, 20, 20)
            }

            AlertDialog.Builder(this)
                .setTitle("📱 QR-код заказа №${order.orderNumber}")
                .setView(imageView)
                .setMessage("Покажите этот QR-код сотруднику ПВЗ")
                .setPositiveButton("OK", null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка генерации QR: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Адаптер для заказов
    inner class OrdersAdapter(private val orders: List<Order>) : RecyclerView.Adapter<OrdersAdapter.OrderViewHolder>() {

        inner class OrderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvNum: TextView = view.findViewById(R.id.tvOrderNum)
            val tvStatus: TextView = view.findViewById(R.id.tvStatus)
            val tvDate: TextView = view.findViewById(R.id.tvDate)
            val recyclerItems: RecyclerView = view.findViewById(R.id.recyclerItems)
            val btnShowQR: Button = view.findViewById(R.id.btnShowQR)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_order, parent, false)
            return OrderViewHolder(view)
        }

        override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
            val order = orders[position]

            holder.tvNum.text = "Заказ №${order.orderNumber}"
            holder.tvStatus.text = order.status

            holder.tvStatus.setTextColor(
                when (order.status) {
                    "Выдан" -> Color.RED
                    "Прибыл" -> Color.parseColor("#4CAF50")
                    else -> Color.parseColor("#FF9800")
                }
            )

            try {
                val dateString = order.createdAt
                if (dateString.length >= 19) {
                    val datePart = dateString.substring(0, 10)
                    val timePart = dateString.substring(11, 19)
                    val parts = datePart.split("-")
                    holder.tvDate.text = "${parts[2]}.${parts[1]}.${parts[0]} $timePart"
                } else {
                    holder.tvDate.text = dateString
                }
            } catch (e: Exception) {
                holder.tvDate.text = order.createdAt
            }

            holder.recyclerItems.layoutManager = LinearLayoutManager(holder.itemView.context)
            holder.recyclerItems.adapter = ItemsAdapter(order.items)

            holder.btnShowQR.setOnClickListener {
                showQRDialog(order)
            }
        }

        override fun getItemCount() = orders.size
    }

    // Адаптер для товаров в заказе
    inner class ItemsAdapter(private val items: List<OrderItem>) : RecyclerView.Adapter<ItemsAdapter.ItemViewHolder>() {

        inner class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvItemName)
            val tvQty: TextView = view.findViewById(R.id.tvItemQty)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_product, parent, false)
            return ItemViewHolder(view)
        }

        override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
            val item = items[position]
            holder.tvName.text = item.name
            holder.tvQty.text = "x${item.quantity}"
        }

        override fun getItemCount() = items.size
    }

    // Адаптер для корзины (добавленных товаров)
    inner class CartAdapter : RecyclerView.Adapter<CartAdapter.CartViewHolder>() {

        inner class CartViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvItemName)
            val tvQty: TextView = view.findViewById(R.id.tvItemQty)
            val btnRemove: ImageButton = view.findViewById(R.id.btnRemove)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CartViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_cart_product, parent, false)
            return CartViewHolder(view)
        }

        override fun onBindViewHolder(holder: CartViewHolder, position: Int) {
            val item = cartItems[position]
            holder.tvName.text = item.name
            holder.tvQty.text = "x${item.quantity}"

            holder.btnRemove.setOnClickListener {
                cartItems.removeAt(position)
                notifyDataSetChanged()
                Toast.makeText(this@MainActivity, "🗑️ Товар удален", Toast.LENGTH_SHORT).show()
            }
        }

        override fun getItemCount() = cartItems.size
    }

    // Класс для товаров в корзине
    data class CartItem(val name: String, val quantity: Int)
}