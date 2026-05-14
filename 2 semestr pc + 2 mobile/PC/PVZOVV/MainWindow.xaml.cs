using System;
using System.Collections.Generic;
using System.Net.Http;
using System.Net.Http.Json;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;

namespace PVZOVV
{
    public partial class MainWindow : Window
    {
        private readonly HttpClient _httpClient;
        private readonly string _baseUrl;

        public List<string> ValidStatuses { get; set; } = new List<string>
        {
            "В пути", "Собирается", "Прибыл", "Выдан"
        };

        public MainWindow()
        {
            InitializeComponent();
            _baseUrl = "http://192.168.143.208:8000"; // ВАШ IP
            _httpClient = new HttpClient();
            LoadOrders();
        }

        private async void LoadOrders()
        {
            try
            {
                var orders = await _httpClient.GetFromJsonAsync<List<Order>>($"{_baseUrl}/orders");
                dgOrders.ItemsSource = orders;
            }
            catch (Exception ex)
            {
                MessageBox.Show($"Ошибка загрузки заказов: {ex.Message}", "Ошибка", MessageBoxButton.OK, MessageBoxImage.Error);
            }
        }

        private async void SaveStatus_Click(object sender, RoutedEventArgs e)
        {
            if (sender is Button btn && btn.Tag is string orderNumber)
            {
                var row = dgOrders.SelectedItem as Order;
                if (row != null)
                {
                    try
                    {
                        var updateData = new { order_number = orderNumber, status = row.status };
                        var response = await _httpClient.PostAsJsonAsync($"{_baseUrl}/update-status", updateData);

                        if (response.IsSuccessStatusCode)
                        {
                            MessageBox.Show($"Статус заказа {orderNumber} обновлен!", "Успех", MessageBoxButton.OK, MessageBoxImage.Information);
                            LoadOrders();
                        }
                        else
                        {
                            var error = await response.Content.ReadAsStringAsync();
                            MessageBox.Show($"Ошибка сервера: {error}", "Ошибка");
                        }
                    }
                    catch (Exception ex)
                    {
                        MessageBox.Show($"Ошибка: {ex.Message}");
                    }
                }
            }
        }

        private void RefreshOrders_Click(object sender, RoutedEventArgs e)
        {
            LoadOrders();
        }

        private async void AddProduct_Click(object sender, RoutedEventArgs e)
        {
            var name = txtProdName.Text.Trim();
            if (string.IsNullOrEmpty(name))
            {
                MessageBox.Show("Введите название товара", "Ошибка", MessageBoxButton.OK, MessageBoxImage.Warning);
                return;
            }

            int qty = int.TryParse(txtProdQty.Text, out var q) ? q : 1;
            double price = double.TryParse(txtProdPrice.Text, out var p) ? p : 0;
            var desc = txtProdDesc.Text.Trim();

            try
            {
                var product = new { name, quantity = qty, price, description = desc };
                var response = await _httpClient.PostAsJsonAsync($"{_baseUrl}/add-product", product);

                if (response.IsSuccessStatusCode)
                {
                    MessageBox.Show("Товар добавлен!", "Успех");
                    txtProdName.Clear();
                    txtProdQty.Text = "1";
                    txtProdPrice.Text = "0";
                    txtProdDesc.Clear();
                    LoadProducts();
                }
            }
            catch (Exception ex)
            {
                MessageBox.Show($"Ошибка: {ex.Message}");
            }
        }

        private async void LoadProducts()
        {
            try
            {
                var products = await _httpClient.GetFromJsonAsync<List<Product>>($"{_baseUrl}/products");
                dgProducts.ItemsSource = products;
            }
            catch (Exception)
            {
                // Игнорируем ошибку если вкладка товаров еще не открыта
            }
        }

        private async void DeleteProduct_Click(object sender, RoutedEventArgs e)
        {
            if (sender is Button btn && btn.Tag is int productId)
            {
                var result = MessageBox.Show("Удалить товар?", "Подтверждение", MessageBoxButton.YesNo, MessageBoxImage.Question);
                if (result == MessageBoxResult.Yes)
                {
                    try
                    {
                        var response = await _httpClient.DeleteAsync($"{_baseUrl}/products/{productId}");
                        if (response.IsSuccessStatusCode)
                        {
                            MessageBox.Show("Товар удален", "Успех");
                            LoadProducts();
                        }
                    }
                    catch (Exception ex)
                    {
                        MessageBox.Show($"Ошибка: {ex.Message}");
                    }
                }
            }
        }
    }

    // ================= МОДЕЛИ ДАННЫХ (ОБЯЗАТЕЛЬНО ОСТАВЬТЕ ИХ ВНИЗУ ФАЙЛА) =================



    public class Product
    {
        public int id { get; set; }
        public string? name { get; set; }
        public int quantity { get; set; }
        public double price { get; set; }
        public string? description { get; set; }
    }
}