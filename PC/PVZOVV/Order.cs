using System.Collections.Generic;
using System.Linq;

namespace PVZOVV;

public class OrderItem
{
    public string name { get; set; } = "";
    public int quantity { get; set; } = 1;
}

public class Order
{
    public string order_number { get; set; } = "";
    public string client_name { get; set; } = "";
    public string phone_number { get; set; } = "";
    public string status { get; set; } = "В пути";
    public string created_at { get; set; } = "";
    public List<OrderItem> items { get; set; } = new();

    public string ItemsSummary => string.Join(", ", items.Select(i => $"{i.name} (x{i.quantity})"));
}