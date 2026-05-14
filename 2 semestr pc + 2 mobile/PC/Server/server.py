from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, HTMLResponse
from pydantic import BaseModel
from typing import List, Optional
from datetime import datetime
import socket
import uvicorn

app = FastAPI(title="PVZ Orders API v3")

@app.middleware("http")
async def add_utf8_header(request, call_next):
    response = await call_next(request)
    response.headers["Content-Type"] = "application/json; charset=utf-8"
    return response

app.add_middleware(
    CORSMiddleware, allow_origins=["*"], allow_credentials=True, allow_methods=["*"], allow_headers=["*"]
)

VALID_STATUSES = ["В пути", "Собирается", "Прибыл", "Выдан"]

# --- МОДЕЛИ ---
class OrderItem(BaseModel):
    name: str
    quantity: int = 1

class OrderCreate(BaseModel):
    order_number: str
    client_name: str
    phone_number: str
    items: List[OrderItem]

class StatusUpdate(BaseModel):
    order_number: str
    status: str

class DeliveryConfirm(BaseModel):
    order_number: str

class Product(BaseModel):
    name: str
    quantity: int
    price: float = 0.0
    description: str = ""

# --- БД (В памяти) ---
orders_db = []
products_db = []

def get_local_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(('8.8.8.8', 80))
        return s.getsockname()[0]
    except: return '127.0.0.1'
    finally: s.close()

# ================= API ЗАКАЗЫ =================
@app.post("/add")
async def add_order(order: OrderCreate):
    if any(o['order_number'] == order.order_number for o in orders_db):
        raise HTTPException(status_code=400, detail="Заказ уже существует")
    new_order = {
        "order_number": order.order_number,
        "client_name": order.client_name,
        "phone_number": order.phone_number,
        "status": "В пути",
        "items": [i.dict() for i in order.items],
        "created_at": datetime.now().isoformat()
    }
    orders_db.append(new_order)
    return {"status": "success", "message": "Order added"}

@app.get("/orders")
async def get_orders():
    return JSONResponse(content=orders_db)

@app.get("/client-orders/{phone_number}")
async def get_client_orders(phone_number: str):
    return JSONResponse(content=[o for o in orders_db if o['phone_number'] == phone_number])

@app.post("/update-status")
async def update_status( StatusUpdate):
    for order in orders_db:
        if order['order_number'] == data.order_number:
            if data.status not in VALID_STATUSES:
                raise HTTPException(status_code=400, detail="Неверный статус")
            order['status'] = data.status
            return {"status": "success", "message": f"Статус: {data.status}"}
    raise HTTPException(status_code=404, detail="Заказ не найден")

@app.post("/confirm-delivery")
async def confirm_delivery( DeliveryConfirm):
    for order in orders_db:
        if order['order_number'] == data.order_number:
            if order['status'] != "Прибыл":
                raise HTTPException(status_code=400, detail="Заказ не прибыл")
            order['status'] = "Выдан"
            return {"status": "success", "message": "Выдано"}
    raise HTTPException(status_code=404, detail="Заказ не найден")

# ================= API ТОВАРЫ =================
@app.get("/products")
async def get_products():
    return JSONResponse(content=products_db)

@app.post("/add-product")
async def add_product(product: Product):
    new_product = {
        "id": len(products_db) + 1,
        "name": product.name,
        "quantity": product.quantity,
        "price": product.price,
        "description": product.description
    }
    products_db.append(new_product)
    return {"status": "success", "message": "Товар добавлен", "id": new_product["id"]}

@app.delete("/products/{product_id}")
async def delete_product(product_id: int):
    global products_db
    products_db = [p for p in products_db if p['id'] != product_id]
    return {"status": "success", "message": "Товар удален"}

# ================= АДМИН ПАНЕЛЬ (ПК) =================
ADMIN_HTML = """
<!DOCTYPE html>
<html lang="ru">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>ПВЗ Админ-панель</title>
    <style>
        body { font-family: sans-serif; padding: 20px; background: #f5f5f5; }
        .tabs { margin-bottom: 20px; }
        .tab-btn { padding: 10px 20px; cursor: pointer; background: #ddd; border: none; margin-right: 5px; }
        .tab-btn.active { background: #2196F3; color: white; }
        .tab-content { display: none; background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 5px rgba(0,0,0,0.1); }
        .tab-content.active { display: block; }
        table { width: 100%; border-collapse: collapse; margin-top: 10px; }
        th, td { padding: 10px; border: 1px solid #ddd; text-align: left; }
        th { background: #f0f0f0; }
        input, select, button { padding: 8px; margin: 5px 0; }
        button { cursor: pointer; background: #4CAF50; color: white; border: none; }
        button.delete { background: #f44336; }
        .form-group { margin-bottom: 10px; }
    </style>
</head>
<body>
    <h1> Админ-панель ПВЗ</h1>
    <div class="tabs">
        <button class="tab-btn active" onclick="switchTab('orders')">📋 Заказы</button>
        <button class="tab-btn" onclick="switchTab('products')">🛒 Товары</button>
    </div>

    <div id="orders" class="tab-content active">
        <h2>Управление заказами</h2>
        <table id="ordersTable">
            <thead><tr><th>№ Заказа</th><th>Клиент</th><th>Телефон</th><th>Статус</th><th>Действие</th></tr></thead>
            <tbody></tbody>
        </table>
    </div>

    <div id="products" class="tab-content">
        <h2>Каталог товаров</h2>
        <div class="form-group">
            <input type="text" id="pName" placeholder="Название товара">
            <input type="number" id="pQty" placeholder="Количество" value="1">
            <input type="number" id="pPrice" placeholder="Цена" value="0">
            <input type="text" id="pDesc" placeholder="Описание">
            <button onclick="addProduct()">➕ Добавить товар</button>
        </div>
        <table id="productsTable">
            <thead><tr><th>ID</th><th>Название</th><th>Кол-во</th><th>Цена</th><th>Описание</th><th>Действие</th></tr></thead>
            <tbody></tbody>
        </table>
    </div>

    <script>
        const API = window.location.origin;
        
        function switchTab(tab) {
            document.querySelectorAll('.tab-content').forEach(el => el.classList.remove('active'));
            document.querySelectorAll('.tab-btn').forEach(el => el.classList.remove('active'));
            document.getElementById(tab).classList.add('active');
            event.target.classList.add('active');
        }

        async function loadData() {
            // Заказы
            const ordersRes = await fetch(API + '/orders');
            const orders = await ordersRes.json();
            const ordersBody = document.querySelector('#ordersTable tbody');
            ordersBody.innerHTML = orders.map(o => `
                <tr>
                    <td>${o.order_number}</td>
                    <td>${o.client_name}</td>
                    <td>${o.phone_number}</td>
                    <td>
                        <select id="status-${o.order_number}">
                            ${["В пути", "Собирается", "Прибыл", "Выдан"].map(s => 
                                `<option value="${s}" ${o.status === s ? 'selected' : ''}>${s}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td><button onclick="changeStatus('${o.order_number}')">💾 Сохранить</button></td>
                </tr>
            `).join('');

            // Товары
            const prodRes = await fetch(API + '/products');
            const products = await prodRes.json();
            const prodBody = document.querySelector('#productsTable tbody');
            prodBody.innerHTML = products.map(p => `
                <tr>
                    <td>${p.id}</td>
                    <td>${p.name}</td>
                    <td>${p.quantity}</td>
                    <td>${p.price} ₽</td>
                    <td>${p.description}</td>
                    <td><button class="delete" onclick="deleteProduct(${p.id})">🗑️</button></td>
                </tr>
            `).join('');
        }

        async function changeStatus(orderNum) {
            const newStatus = document.getElementById(`status-${orderNum}`).value;
            const res = await fetch(API + '/update-status', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({order_number: orderNum, status: newStatus})
            });
            const data = await res.json();
            alert(data.message || 'Готово');
            loadData();
        }

        async function addProduct() {
            const name = document.getElementById('pName').value;
            const qty = parseInt(document.getElementById('pQty').value);
            const price = parseFloat(document.getElementById('pPrice').value);
            const desc = document.getElementById('pDesc').value;
            if (!name) return alert('Введите название');
            
            await fetch(API + '/add-product', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({name, quantity: qty, price, description: desc})
            });
            document.getElementById('pName').value = '';
            loadData();
        }

        async function deleteProduct(id) {
            if (!confirm('Удалить товар?')) return;
            await fetch(API + `/products/${id}`, {method: 'DELETE'});
            loadData();
        }

        loadData();
        setInterval(loadData, 10000); // Автообновление каждые 10 сек
    </script>
</body>
</html>
"""

@app.get("/admin", response_class=HTMLResponse)
async def admin_panel():
    return ADMIN_HTML

# ================= ЗАПУСК =================
if __name__ == "__main__":
    ip = get_local_ip()
    print(f" Сервер запущен: http://{ip}:8000")
    print(f"📊 Админ-панель: http://{ip}:8000/admin")
    uvicorn.run(app, host="0.0.0.0", port=8000)