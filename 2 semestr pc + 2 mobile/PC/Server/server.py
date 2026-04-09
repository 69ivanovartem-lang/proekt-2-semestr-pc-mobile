from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import List, Optional, Literal
from datetime import datetime
import socket
import uvicorn
import json

app = FastAPI(title="PVZ Orders API")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Допустимые статусы
VALID_STATUSES = ["В пути", "Собирается", "Прибыл", "Выдан"]

class Order(BaseModel):
    pzv_number: str
    order_number: str
    client_name: str
    phone_number: str
    status: str = "В пути"
    created_at: Optional[str] = None
    delivered_at: Optional[str] = None

class OrderCreate(BaseModel):
    pzv_number: str
    order_number: str
    client_name: str
    phone_number: str

class StatusUpdate(BaseModel):
    order_number: str
    status: str

class DeliveryConfirm(BaseModel):
    order_number: str
    confirmed: bool

orders_db = []

def get_local_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(('8.8.8.8', 80))
        ip = s.getsockname()[0]
    except:
        ip = '127.0.0.1'
    finally:
        s.close()
    return ip

@app.get("/")
async def root():
    return {"message": "PVZ Orders API", "time": datetime.now().isoformat()}

@app.get("/server-info")
async def server_info():
    local_ip = get_local_ip()
    return {
        "server_url": f"http://{local_ip}:8000",
        "qr_data": f"PVZ|{local_ip}|8000"
    }

@app.post("/add")
async def add_order(order: OrderCreate):
    for o in orders_db:
        if o['order_number'] == order.order_number:
            raise HTTPException(status_code=400, detail="Заказ уже существует")
    
    new_order = Order(
        pzv_number=order.pzv_number,
        order_number=order.order_number,
        client_name=order.client_name,
        phone_number=order.phone_number,
        status="В пути",  # По умолчанию "В пути"
        created_at=datetime.now().isoformat()
    )
    orders_db.append(new_order.dict())
    print(f"✅ Новый заказ: {order.order_number} - {order.client_name}")
    return {"status": "success", "message": "Order added"}

@app.get("/orders")
async def get_orders():
    return orders_db

@app.post("/update-status")
async def update_status( StatusUpdate):
    """Изменение статуса заказа"""
    if data.status not in VALID_STATUSES:
        raise HTTPException(status_code=400, detail=f"Неверный статус. Допустимы: {VALID_STATUSES}")
    
    for order in orders_db:
        if order['order_number'] == data.order_number:
            order['status'] = data.status
            print(f"📝 Заказ {data.order_number}: статус изменён на '{data.status}'")
            return {"status": "success", "message": f"Статус изменён на {data.status}"}
    
    raise HTTPException(status_code=404, detail="Заказ не найден")

@app.post("/confirm-delivery")
async def confirm_delivery(data: DeliveryConfirm):
    """Подтверждение выдачи заказа"""
    for order in orders_db:
        if order['order_number'] == data.order_number:
            if order['status'] != "Прибыл":
                raise HTTPException(
                    status_code=400, 
                    detail=f"Нельзя выдать заказ! Текущий статус: {order['status']}. Заказ должен иметь статус 'Прибыл'"
                )
            
            if data.confirmed:
                order['status'] = "Выдан"
                order['delivered_at'] = datetime.now().isoformat()
                print(f"✅ Заказ {data.order_number} ВЫДАН!")
                return {"status": "success", "message": "Заказ выдан"}
            else:
                return {"status": "cancelled", "message": "Выдача отменена"}
    
    raise HTTPException(status_code=404, detail="Заказ не найден")

@app.delete("/orders/clear")
async def clear_orders():
    global orders_db
    orders_db = []
    return {"status": "success", "message": "All orders cleared"}

if __name__ == "__main__":
    local_ip = get_local_ip()
    print(f"\n🚀 Сервер запущен!")
    print(f"📱 IP: {local_ip}")
    print(f"🔗 URL: http://{local_ip}:8000")
    uvicorn.run(app, host="192.168.143.208", port=8000)