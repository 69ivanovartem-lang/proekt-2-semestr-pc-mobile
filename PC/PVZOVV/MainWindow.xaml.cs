using System;
using System.Collections.Generic;
using System.Net.Http;
using System.Windows;
using System.Windows.Controls;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;

namespace PVZOVV;

public partial class MainWindow : Window
{
    private readonly HttpClient client = new();
    private string serverUrl = "http://localhost:8000";

    public MainWindow()
    {
        InitializeComponent();
        LoadServerInfo();
    }

    private async void LoadServerInfo()
    {
        try
        {
            var info = await client.GetStringAsync($"{serverUrl}/server-info");
            var json = JObject.Parse(info);
            serverUrl = json["server_url"]?.ToString() ?? "http://localhost:8000";
            await RefreshOrders();
        }
        catch (Exception ex)
        {
            MessageBox.Show($"Ошибка подключения: {ex.Message}");
        }
    }

    private async void LoadData_Click(object sender, RoutedEventArgs e)
    {
        await RefreshOrders();
    }

    private async System.Threading.Tasks.Task RefreshOrders()
    {
        try
        {
            var json = await client.GetStringAsync($"{serverUrl}/orders");
            var orders = JsonConvert.DeserializeObject<List<Order>>(json) ?? new List<Order>();
            OrdersDataGrid.ItemsSource = orders;
        }
        catch (Exception ex)
        {
            MessageBox.Show($"Ошибка загрузки: {ex.Message}");
        }
    }

    private void OrdersDataGrid_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (OrdersDataGrid.SelectedItem is Order order)
        {
            ItemsDataGrid.ItemsSource = order.items;
        }
        else
        {
            ItemsDataGrid.ItemsSource = null;
        }
    }
}