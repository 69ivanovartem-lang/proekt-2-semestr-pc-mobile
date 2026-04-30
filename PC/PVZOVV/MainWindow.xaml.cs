using System;
using System.Collections.Generic;
using System.Net.Http;
using System.Windows;
using System.Windows.Media;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;

namespace PVZOVV;

public partial class MainWindow : Window
{
    private readonly HttpClient client = new HttpClient();
    private string serverUrl = "http://192.168.143.208:8000";
    private Order? selectedOrder;

    public MainWindow()
    {
        InitializeComponent();
        LoadServerInfo();
    }

    private async void LoadServerInfo()
    {
        try
        {
            var response = await client.GetStringAsync($"{serverUrl}/server-info");
            var json = JObject.Parse(response);
            serverUrl = json["server_url"]?.ToString() ?? "http://localhost:8000";

            StatusText.Text = $"✅ Сервер: {serverUrl}";
            StatusText.Foreground = Brushes.Green;

            await RefreshOrders();
        }
        catch (Exception ex)
        {
            StatusText.Text = $"❌ Ошибка: {ex.Message}";
            StatusText.Foreground = Brushes.Red;
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
            var response = await client.GetStringAsync($"{serverUrl}/orders");
            var orders = JsonConvert.DeserializeObject<List<Order>>(response) ?? new List<Order>();
            OrdersDataGrid.ItemsSource = orders;
            LastUpdateText.Text = $"Обновлено: {DateTime.Now:HH:mm:ss}";
        }
        catch (Exception ex)
        {
            MessageBox.Show($"Ошибка загрузки: {ex.Message}");
        }
    }

    // Генерация QR-кода для выбранного заказа
    private void OrdersDataGrid_SelectionChanged(object sender, System.Windows.Controls.SelectionChangedEventArgs e)
    {
        if (OrdersDataGrid.SelectedItem is Order order)
        {
            selectedOrder = order;
            // Формат QR: ORDER|номер_заказа|клиент|телефон|ПВЗ
            string qrData = $"ORDER|{order.order_number}|{order.client_name}|{order.phone_number}|{order.pzv_number}";
            GenerateQRCode(qrData);
        }
    }

    private void GenerateQRCode(string qrData)
    {
        try
        {
            // Используем QRCoder из вашей библиотеки
            var qrGenerator = new QRCoder.QRCodeGenerator();
            var qrCodeData = qrGenerator.CreateQrCode(qrData, QRCoder.QRCodeGenerator.ECCLevel.Q);
            var qrCode = new QRCoder.QRCode(qrCodeData);

            var qrCodeImage = qrCode.GetGraphic(20);

            using (var ms = new System.IO.MemoryStream())
            {
                qrCodeImage.Save(ms, System.Drawing.Imaging.ImageFormat.Png);
                ms.Position = 0;

                var bitmap = new System.Windows.Media.Imaging.BitmapImage();
                bitmap.BeginInit();
                bitmap.StreamSource = ms;
                bitmap.CacheOption = System.Windows.Media.Imaging.BitmapCacheOption.OnLoad;
                bitmap.EndInit();
                bitmap.Freeze();

                QRCodeImage.Source = bitmap;
            }
        }
        catch (Exception ex)
        {
            MessageBox.Show($"Ошибка QR: {ex.Message}");
        }
    }

    private async void ClearOrders_Click(object sender, RoutedEventArgs e)
    {
        var result = MessageBox.Show("Очистить все заказы?", "Подтверждение",
            MessageBoxButton.YesNo, MessageBoxImage.Question);

        if (result == MessageBoxResult.Yes)
        {
            try
            {
                await client.DeleteAsync($"{serverUrl}/orders/clear");
                await RefreshOrders();
            }
            catch (Exception ex)
            {
                MessageBox.Show($"Ошибка: {ex.Message}");
            }
        }
    }

    protected override void OnClosed(EventArgs e)
    {
        client.Dispose();
        base.OnClosed(e);
    }
}