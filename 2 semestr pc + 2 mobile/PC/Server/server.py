from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from pydantic import BaseModel
from typing import List, Optional
from datetime import datetime
import socket
import uvicorn

app = FastAPI(title="PVZ Orders API v2")

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

# --- БД (В памяти) ---
orders_db = []

def get_local_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(('8.8.8.8', 80))
        return s.getsockname()[0]
    except: return '127.0.0.1'
    finally: s.close()

@app.get("/server-info")
async def server_info():
    return {"server_url": f"http://{get_local_ip()}:8000"}

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
    print(f"✅ Заказ {order.order_number} ({len(order.items)} тов.)")
    return {"status": "success", "message": "Order added"}

@app.get("/orders")
async def get_orders():
    return JSONResponse(content=orders_db)

@app.get("/client-orders/{phone_number}")
async def get_client_orders(phone_number: str):
    client_orders = [o for o in orders_db if o['phone_number'] == phone_number]
    return JSONResponse(content=client_orders)

# ✅ ИСПРАВЛЕНО: принимаем как dict (словарь), чтобы избежать ошибок валидации
# ✅ ИСПРАВЛЕНО: принимаем как dict (словарь), чтобы избежать ошибок валидации
@app.post("/update-status")
async def update_status(update_data: dict):
    print(f"📥 Получено: {update_data}")  # ЛОГ чтобы видеть что пришло
    order_number = update_data.get("order_number")
    new_status = update_data.get("status")
    
    print(f"🔍 Ищу заказ: {order_number}, новый статус: {new_status}")
    
    for order in orders_db:
        if order['order_number'] == order_number:
            if new_status not in VALID_STATUSES:
                print(f"❌ Неверный статус: {new_status}")
                raise HTTPException(status_code=400, detail="Неверный статус")
            order['status'] = new_status
            print(f"✅ Статус изменен для {order_number}")
            return {"status": "success", "message": f"Статус: {new_status}"}
    
    print(f"❌ Заказ не найден: {order_number}")
    raise HTTPException(status_code=404, detail="Заказ не найден")

@app.post("/confirm-delivery")
async def confirm_delivery(data: dict):
    print(f"📥 Получено на выдачу: {data}")
    order_number = data.get("order_number")
    
    for order in orders_db:
        if order['order_number'] == order_number:
            if order['status'] != "Прибыл":
                raise HTTPException(status_code=400, detail="Заказ не прибыл")
            order['status'] = "Выдан"
            print(f"✅ Заказ {order_number} выдан!")
            return {"status": "success", "message": "Выдано"}
            
    raise HTTPException(status_code=404, detail="Заказ не найден")

@app.delete("/orders/clear")
async def clear_orders():
    global orders_db
    orders_db = []
    return {"status": "success", "message": "Cleared"}

if __name__ == "__main__":
    print(f"🚀 Сервер: http://{get_local_ip()}:8000")
    uvicorn.run(app, host="0.0.0.0", port=8000)