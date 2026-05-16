#include <WiFi.h>
#include <DHT.h>
#include <FirebaseESP32.h>
#include "secrets.h"

// 3. Định nghĩa chân kết nối (GPIO)
#define DHTPIN 23
#define DHTTYPE DHT11
#define BUTTON_PIN 4
#define BUZZER_PIN 18

// Khởi tạo các đối tượng
DHT dht(DHTPIN, DHTTYPE);
FirebaseData firebaseDataStream; 
FirebaseData firebaseDataWrite;  
FirebaseAuth auth;
FirebaseConfig config;

// Các biến quản lý thời gian
unsigned long lastSendTime = 0;
const unsigned long sendInterval = 3000; // Gửi dữ liệu DHT11 mỗi 3 giây

// Biến quản lý trạng thái hệ thống
int currentBuzzerState = 0;       // 0: Tắt, 1: Bật (Do người dùng hoặc nhiệt độ kích hoạt)
float tempThreshold = 35.0;       // Ngưỡng nhiệt độ mặc định, sẽ bị ghi đè nếu thay đổi trên Firebase
float currentTemperature = 0.0;

// Biến chống dội phím (Debounce Button)
int lastButtonState = HIGH;
unsigned long lastDebounceTime = 0;
unsigned long debounceDelay = 50; // 50ms để lọc nhiễu khi bấm nút

// Hàm Callback xử lý khi Firebase thay đổi trạng thái hệ thống (Stream)
void streamCallback(StreamData data) {
  String path = data.dataPath();
  
  // Trường hợp 1: Nhận lệnh điều khiển Buzzer (từ App hoặc chính nút bấm gửi lên)
  if (path == "/buzzer_trigger") {
    if (data.dataType() == "int") {
      currentBuzzerState = data.intData();
      Serial.printf("Trạng thái còi cập nhật: %d\n", currentBuzzerState);
      digitalWrite(BUZZER_PIN, currentBuzzerState == 1 ? HIGH : LOW);
    }
  }
  
  // Trường hợp 2: Nhận ngưỡng nhiệt độ cấu hình từ App Android
  else if (path == "/temperature_threshold") {
    if (data.dataType() == "float" || data.dataType() == "int") {
      tempThreshold = data.floatData();
      Serial.printf("Ngưỡng nhiệt độ cảnh báo mới: %.1f °C\n", tempThreshold);
    }
  }
}

void streamTimeoutCallback(bool timeout) {
  if (timeout) Serial.println("Stream bị timeout, đang tự động kết nối lại...");
}

void setup() {
  Serial.begin(115200);
  
  dht.begin();
  delay(2000); // Chờ DHT11 ổn định
  
  pinMode(BUTTON_PIN, INPUT_PULLUP);
  pinMode(BUZZER_PIN, OUTPUT);
  digitalWrite(BUZZER_PIN, LOW);     

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

  // Bắt đầu lắng nghe "Stream" toàn bộ thư mục gốc của dự án để nhận nhiều biến cùng lúc
  if (!Firebase.beginStream(firebaseDataStream, "/iot_project")) {
    Serial.println("Không thể thiết lập Stream: " + firebaseDataStream.errorReason());
  }
  Firebase.setStreamCallback(firebaseDataStream, streamCallback, streamTimeoutCallback);

  // Khởi tạo sẵn biến ngưỡng trên Firebase nếu chưa có
  Firebase.setFloat(firebaseDataWrite, "/iot_project/temperature_threshold", tempThreshold);
}

void loop() {
  unsigned long currentMillis = millis();

  // --- CHIỀU 1: ĐỌC DHT11 & XỬ LÝ CẢNH BÁO NHIỆT ĐỘ ---
  if (currentMillis - lastSendTime >= sendInterval) {
    lastSendTime = currentMillis;

    float h = dht.readHumidity();
    float t = dht.readTemperature();

    if (!isnan(h) && !isnan(t)) {
      currentTemperature = t;
      Serial.printf("Nhiệt độ: %.1f °C | Độ ẩm: %.1f %% | Ngưỡng: %.1f °C\n", t, h, tempThreshold);
      
      // Gửi dữ liệu lên Firebase
      Firebase.setFloat(firebaseDataWrite, "/iot_project/temperature", t);
      Firebase.setFloat(firebaseDataWrite, "/iot_project/humidity", h);

      // KIỂM TRA NGƯỠNG NHIỆT ĐỘ
      if (currentTemperature > tempThreshold) {
        if (currentBuzzerState == 0) { // Nếu còi đang tắt thì mới kích hoạt bật
          Serial.println("!!! CẢNH BÁO: Nhiệt độ vượt ngưỡng !!!");
          Firebase.setInt(firebaseDataWrite, "/iot_project/buzzer_trigger", 1); 
          // Khi update lên Firebase, hàm streamCallback tự kích hoạt còi kêu
        }
      }
    }
  }

  // --- CHIỀU 2: XỬ LÝ NÚT NHẤN CHUYỂN TRẠNG THÁI (TOGGLE) ---
  int reading = digitalRead(BUTTON_PIN);

  // Nếu trạng thái chân nút bấm thay đổi (do nhấn hoặc do nhiễu)
  if (reading != lastButtonState) {
    lastDebounceTime = currentMillis; // Reset lại thời gian chờ chống dội
  }

  if ((currentMillis - lastDebounceTime) > debounceDelay) {
    // Nếu trạng thái đã ổn định qua thời gian lọc nhiễu, kiểm tra xem có thực sự đổi trạng thái không
    static int stabilizedButtonState = HIGH;
    if (reading != stabilizedButtonState) {
      stabilizedButtonState = reading;

      // Phát hiện khoảnh khắc NÚT VỪA ĐƯỢC NHẤN XUỐNG (Mức LOW)
      if (stabilizedButtonState == LOW) {
        Serial.println("Nút được bấm -> Đảo trạng thái còi");
        
        // Đảo trạng thái hiện tại: nếu đang 1 thành 0, nếu đang 0 thành 1
        int nextBuzzerState = (currentBuzzerState == 1) ? 0 : 1;
        
        // Đẩy trạng thái mới lên Firebase để đồng bộ cho cả App và Còi
        Firebase.setInt(firebaseDataWrite, "/iot_project/buzzer_trigger", nextBuzzerState);
      }
    }
  }
  lastButtonState = reading;
}