#include <WiFi.h>
#include <DHT.h>
#include <FirebaseESP32.h>
#include "secrets.h"

// Định nghĩa chân kết nối (GPIO)
#define DHTPIN 23
#define DHTTYPE DHT11

// Định nghĩa chân đọc Analog (ADC)
#define SOIL_MOISTURE_PIN 34
#define POT_N_PIN 35
#define POT_P_PIN 32
#define POT_K_PIN 33

// Khởi tạo các đối tượng
DHT dht(DHTPIN, DHTTYPE);
FirebaseData firebaseDataWrite;  
FirebaseAuth auth;
FirebaseConfig config;

// Các biến quản lý thời gian
unsigned long lastSendTime = 0;
const unsigned long sendInterval = 3000; // Gửi dữ liệu DHT11 mỗi 3 giây

// Giới hạn giá trị giả lập NPK
const int NPK_MAX_VALUE = 200;

void setup() {
  Serial.begin(115200);
  
  dht.begin();
  delay(2000); // Chờ DHT11 ổn định    

  // Cấu hình độ phân giải ADC là 12-bit (đọc giá trị từ 0 - 4095)
  analogReadResolution(12);

  // Kết nối WiFi
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("Đang kết nối WiFi");
  while (WiFi.status() != WL_CONNECTED) {
    Serial.print(".");
    delay(500);
  }
  Serial.println("\nWiFi đã kết nối!");

  // Cấu hình Firebase
  config.host = FIREBASE_HOST;
  config.signer.tokens.legacy_token = FIREBASE_AUTH;
  config.cert.data = NULL; // Sửa lỗi SSL engine closed

  Firebase.reconnectWiFi(true);
  Firebase.begin(&config, &auth);

}

void loop() {
  unsigned long currentMillis = millis();

  // Đọc và gửi dữ liệu định kỳ, không dùng hàm delay() để tránh treo chip
  if (currentMillis - lastSendTime >= sendInterval) {
    lastSendTime = currentMillis;

    // a. Đọc dữ liệu từ cảm biến không khí DHT11
    float airTemp = dht.readTemperature();
    float airHumid = dht.readHumidity();

    // b. Đọc cảm biến độ ẩm đất và chuyển đổi sang tỷ lệ % (0 - 100%)
    int rawSoil = analogRead(SOIL_MOISTURE_PIN);
    // Lưu ý: 3200 là giá trị lúc khô ráo, 1500 là lúc cắm vào nước. Bạn có thể tinh chỉnh lại sau.
    int soilMoisturePercent = map(rawSoil, 0, 900, 0, 100);
    soilMoisturePercent = constrain(soilMoisturePercent, 0, 100); 

    // c. Đọc 3 biến trở giả lập chỉ số NPK (Từ 0-4095 sang 0-200 mg/kg)
    int rawN = analogRead(POT_N_PIN);
    int rawP = analogRead(POT_P_PIN);
    int rawK = analogRead(POT_K_PIN);

    int valN = map(rawN, 0, 4095, 0, NPK_MAX_VALUE);
    int valP = map(rawP, 0, 4095, 0, NPK_MAX_VALUE);
    int valK = map(rawK, 0, 4095, 0, NPK_MAX_VALUE);

    // Kiểm tra nếu cảm biến DHT hoạt động bình thường thì tiến hành đẩy dữ liệu
    if (!isnan(airTemp) && !isnan(airHumid)) {
      // In ra Serial Monitor để giám sát tại chỗ
      Serial.printf("[KK] T: %.1f°C, H: %.1f%% | [ĐẤT] Ẩm: %d%% | [NPK] N: %d, P: %d, K: %d mg/kg\n", 
                    airTemp, airHumid, soilMoisturePercent, valN, valP, valK);

      // Đẩy đồng loạt các thông số lên nút gốc "/smart_agriculture" trên Firebase
      Firebase.setFloat(firebaseDataWrite, "/smart_agriculture/air_temperature", airTemp);
      Firebase.setFloat(firebaseDataWrite, "/smart_agriculture/air_humidity", airHumid);
      Firebase.setInt(firebaseDataWrite, "/smart_agriculture/soil_moisture", soilMoisturePercent);
      Firebase.setInt(firebaseDataWrite, "/smart_agriculture/npk_n", valN);
      Firebase.setInt(firebaseDataWrite, "/smart_agriculture/npk_p", valP);
      Firebase.setInt(firebaseDataWrite, "/smart_agriculture/npk_k", valK);
    } else {
      Serial.println("Lỗi: Không đọc được dữ liệu từ cảm biến DHT!");
    }
  }
}