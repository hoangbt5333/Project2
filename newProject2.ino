#include <WiFi.h>
#include <DHT.h>
#include <FirebaseESP32.h>
#include "secrets.h" // File chứa thông tin cấu hình WiFi và Firebase

// 3. Định nghĩa chân kết nối (GPIO)
#define DHTPIN 23
#define DHTTYPE DHT11
#define BUTTON_PIN 4
#define BUZZER_PIN 18

// Khởi tạo các đối tượng
DHT dht(DHTPIN, DHTTYPE);
FirebaseData firebaseDataStream; // Đối tượng riêng dành cho việc lắng nghe Stream (nhận lệnh)
FirebaseData firebaseDataWrite;  // Đối tượng riêng dành cho việc đẩy dữ liệu lên (DHT11, Button)
FirebaseAuth auth;
FirebaseConfig config;

// Các biến quản lý thời gian gửi dữ liệu (Tránh dùng delay() làm mất kết nối Stream)
unsigned long lastSendTime = 0;
const unsigned long sendInterval = 3000; // Gửi dữ liệu DHT11 mỗi 3 giây

// Biến lưu trạng thái nút bấm trước đó để tránh gửi trùng lặp liên tục
int lastButtonState = HIGH; 

// Hàm Callback xử lý khi Firebase thay đổi trạng thái Buzzer (Chiều nhận lệnh từ Android)
void streamCallback(StreamData data) {
  if (data.dataType() == "int") {
    int buzzerStatus = data.intData();
    Serial.printf("Nhận lệnh còi Buzzer từ Firebase: %d\n", buzzerStatus);
    
    if (buzzerStatus == 1) {
      digitalWrite(BUZZER_PIN, HIGH); // Bật còi
    } else {
      digitalWrite(BUZZER_PIN, LOW);  // Tắt còi
    }
  }
}

// Hàm Callback xử lý khi đường truyền Stream bị ngắt quãng
void streamTimeoutCallback(bool timeout) {
  if (timeout) {
    Serial.println("Stream bị timeout, đang tự động kết nối lại...");
  }
}

void setup() {
  Serial.begin(115200);
  
  // Cấu hình phần cứng
  dht.begin();
  pinMode(BUTTON_PIN, INPUT_PULLUP); // Dùng điện trở kéo lên nội bộ cho nút nhấn
  pinMode(BUZZER_PIN, OUTPUT);
  digitalWrite(BUZZER_PIN, LOW);     // Mặc định còi tắt

  // Kết nối WiFi
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("Đang kết nối WiFi");
  while (WiFi.status() != WL_CONNECTED) {
    Serial.print(".");
    delay(500);
  }
  Serial.println("\nWiFi đã kết nối thành công!");
  Serial.print("Địa chỉ IP: ");
  Serial.println(WiFi.localIP());

  // Cấu hình Firebase
  config.host = FIREBASE_HOST;
  config.signer.tokens.legacy_token = FIREBASE_AUTH;
  
  Firebase.reconnectWiFi(true);
  Firebase.begin(&config, &auth);

  // Bắt đầu lắng nghe "Stream" từ ô dữ liệu 'buzzer_trigger' trên Firebase
  if (!Firebase.beginStream(firebaseDataStream, "/iot_project/buzzer_trigger")) {
    Serial.println("Không thể thiết lập Stream lắng nghe Firebase: " + firebaseDataStream.errorReason());
  }
  
  // Đăng ký các hàm xử lý sự kiện Stream
  Firebase.setStreamCallback(firebaseDataStream, streamCallback, streamTimeoutCallback);
}

void loop() {
  unsigned long currentMillis = millis();

  // CHIỀU 1: GỬI NHIỆT ĐỘ, ĐỘ ẨM LÊN FIREBASE ĐỊNH KỲ
  if (currentMillis - lastSendTime >= sendInterval) {
    lastSendTime = currentMillis;

    float h = dht.readHumidity();
    float t = dht.readTemperature();

    // Kiểm tra xem cảm biến đọc có lỗi không
    if (isnan(h) || isnan(t)) {
      Serial.println("Lỗi đọc từ cảm biến DHT11!");
    } else {
      Serial.printf("Nhiệt độ: %.1f °C | Độ ẩm: %.1f %%\n", t, h);
      
      // Đẩy dữ liệu lên Firebase
      if (Firebase.setFloat(firebaseDataWrite, "/iot_project/temperature", t)) {
        Serial.println("-> Cập nhật Nhiệt độ thành công.");
      } else {
        Serial.println("Lỗi cập nhập Nhiệt độ: " + firebaseDataWrite.errorReason());
      }
      if (Firebase.setFloat(firebaseDataWrite, "/iot_project/humidity", h)) {
        Serial.println("-> Cập nhật Độ ẩm thành công.");
      } else {
        Serial.println("Lỗi cập nhập Độ ẩm: " + firebaseDataWrite.errorReason());
      }
    }
  }

  // CHIỀU 2: KIỂM TRA NÚT NHẤN VÀ CẬP NHẬT TRẠNG THÁI TỨC THÌ
  int currentButtonState = digitalRead(BUTTON_PIN);
  if (currentButtonState != lastButtonState) {
    lastButtonState = currentButtonState;
    
    // Nếu nút được nhấn (Mức LOW do dùng INPUT_PULLUP)
    if (currentButtonState == LOW) {
      Serial.println("Nút nhấn được KÍCH HOẠT!");
      Firebase.setBool(firebaseDataWrite, "/iot_project/button_pressed", true);
    } else {
      Serial.println("Nút nhấn được THẢ RA!");
      Firebase.setBool(firebaseDataWrite, "/iot_project/button_pressed", false);
    }
  }
}