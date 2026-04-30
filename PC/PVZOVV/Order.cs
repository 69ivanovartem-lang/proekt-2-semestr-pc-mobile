namespace PVZOVV;

public class Order
{
    public string pzv_number { get; set; } = string.Empty;
    public string order_number { get; set; } = string.Empty;
    public string client_name { get; set; } = string.Empty;
    public string phone_number { get; set; } = string.Empty;
    public string status { get; set; } = "Прибыл";
}