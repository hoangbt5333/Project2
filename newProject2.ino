#include <WiFi.h>
#include <DHT.h>
#include <FirebaseESP32.h>
#include "secrets.h"

// ================= SENSOR =================
#define DHTPIN 23
#define DHTTYPE DHT11

#define SOIL_MOISTURE_PIN 34
// Đã loại bỏ hoàn toàn các chân biến trở cũ

// ================= RELAY =================
#define RELAY_PUMP_PIN 26
#define RELAY_FAN_PIN 27

// Nhiều relay module kích LOW.
const bool RELAY_ACTIVE_LOW = true;

// ================= OBJECTS =================
DHT dht(DHTPIN, DHTTYPE);

FirebaseData firebaseDataWrite;
FirebaseData firebaseDataRead;

FirebaseAuth auth;
FirebaseConfig config;

// ================= TIME =================
unsigned long lastSendTime = 0;
const unsigned long sendInterval = 3000;

// ================= SOIL CALIBRATION =================
const int SOIL_DRY_RAW = 3200;
const int SOIL_WET_RAW = 1500;

// ================= CONTROL & VARIABLES =================
bool autoMode = true;
bool manualPump = false;
bool manualFan = false;

int soilThreshold = 40;
float tempThreshold = 35.0;
const float TEMP_HYSTERESIS = 2.0;

bool pumpRunning = false;
bool fanRunning = false;

// Các biến lưu giá trị đọc được từ Firebase về
int valN = 0;
int valP = 0;
int valK = 0;
float soilPh = 7.0; // Thêm biến lưu giá trị pH (mặc định để trung tính 7.0)

// ================= RELAY HELPERS =================
void writeRelay(int pin, bool on) {
  if (RELAY_ACTIVE_LOW) {
    digitalWrite(pin, on ? LOW : HIGH);
  } else {
    digitalWrite(pin, on ? HIGH : LOW);
  }
}

void turnAllRelayOff() {
  writeRelay(RELAY_PUMP_PIN, false);
  writeRelay(RELAY_FAN_PIN, false);
}

// ================= SENSOR HELPERS =================
int readAnalogAverage(int pin, int samples = 10) {
  long total = 0;
  for (int i = 0; i < samples; i++) {
    total += analogRead(pin);
    delay(5);
  }
  return total / samples;
}

int mapSoilToPercent(int rawSoil) {
  int percent = map(rawSoil, SOIL_DRY_RAW, SOIL_WET_RAW, 0, 100);
  return constrain(percent, 0, 100);
}

// ================= FIREBASE CONTROL (ĐÃ THÊM LẤY pH) =================
void readControlFromFirebase() {
  if (Firebase.getBool(firebaseDataRead, "/smart_agriculture/control/auto_mode")) {
    autoMode = firebaseDataRead.boolData();
  } else {
    Serial.print("Read auto_mode failed: ");
    Serial.println(firebaseDataRead.errorReason());
  }

  if (Firebase.getBool(firebaseDataRead, "/smart_agriculture/control/pump")) {
    manualPump = firebaseDataRead.boolData();
  } else {
    Serial.print("Read pump failed: ");
    Serial.println(firebaseDataRead.errorReason());
  }

  if (Firebase.getBool(firebaseDataRead, "/smart_agriculture/control/fan")) {
    manualFan = firebaseDataRead.boolData();
  } else {
    Serial.print("Read fan failed: ");
    Serial.println(firebaseDataRead.errorReason());
  }

  if (Firebase.getInt(firebaseDataRead, "/smart_agriculture/control/soil_threshold")) {
    soilThreshold = constrain(firebaseDataRead.intData(), 0, 100);
  } else {
    Serial.print("Read soil_threshold failed: ");
    Serial.println(firebaseDataRead.errorReason());
  }

  if (Firebase.getFloat(firebaseDataRead, "/smart_agriculture/control/temp_threshold")) {
    tempThreshold = constrain(firebaseDataRead.floatData(), 0.0, 60.0);
  } else {
    Serial.print("Read temp_threshold failed: ");
    Serial.println(firebaseDataRead.errorReason());
  }

  // --- ĐỌC GIÁ TRỊ NPK TỪ FIREBASE VỀ ---
  if (Firebase.getInt(firebaseDataRead, "/smart_agriculture/npk_n")) {
    valN = firebaseDataRead.intData();
  }
  if (Firebase.getInt(firebaseDataRead, "/smart_agriculture/npk_p")) {
    valP = firebaseDataRead.intData();
  }
  if (Firebase.getInt(firebaseDataRead, "/smart_agriculture/npk_k")) {
    valK = firebaseDataRead.intData();
  }

  // --- ĐỌC GIÁ TRỊ pH TỪ FIREBASE VỀ (MỚI THÊM) ---
  if (Firebase.getFloat(firebaseDataRead, "/smart_agriculture/soil_ph")) {
    soilPh = firebaseDataRead.floatData();
  } else {
    Serial.print("Read soil_ph failed: ");
    Serial.println(firebaseDataRead.errorReason());
  }
}

// ================= APPLY CONTROL =================
void applyControl(int soilMoisturePercent, float airTemp) {
  if (autoMode) {
    pumpRunning = soilMoisturePercent < soilThreshold;

    if (airTemp >= tempThreshold) {
      fanRunning = true;
    } else if (airTemp <= tempThreshold - TEMP_HYSTERESIS) {
      fanRunning = false;
    }
  } else {
    pumpRunning = manualPump;
    fanRunning = manualFan;
  }

  writeRelay(RELAY_PUMP_PIN, pumpRunning);
  writeRelay(RELAY_FAN_PIN, fanRunning);
}

// ================= SEND FIREBASE =================
void sendSensorDataToFirebase(float airTemp, float airHumid, int soilMoisturePercent) {
  Firebase.setFloat(firebaseDataWrite, "/smart_agriculture/air_temperature", airTemp);
  Firebase.setFloat(firebaseDataWrite, "/smart_agriculture/air_humidity", airHumid);
  Firebase.setInt(firebaseDataWrite, "/smart_agriculture/soil_moisture", soilMoisturePercent);
  
  // Không ghi đè lên các trường npk_n, npk_p, npk_k, soil_ph để app thoải mái chỉnh sửa dữ liệu
}

void sendDeviceStateToFirebase() {
  Firebase.setBool(firebaseDataWrite, "/smart_agriculture/state/pump_running", pumpRunning);
  Firebase.setBool(firebaseDataWrite, "/smart_agriculture/state/fan_running", fanRunning);
  Firebase.setString(firebaseDataWrite, "/smart_agriculture/state/mode", autoMode ? "AUTO" : "MANUAL");

  Firebase.setBool(firebaseDataWrite, "/smart_agriculture/state/relay1", pumpRunning);
  Firebase.setBool(firebaseDataWrite, "/smart_agriculture/state/relay2", fanRunning);
}

// ================= SETUP =================
void setup() {
  Serial.begin(115200);

  dht.begin();
  delay(2000);

  pinMode(RELAY_PUMP_PIN, OUTPUT);
  pinMode(RELAY_FAN_PIN, OUTPUT);
  turnAllRelayOff();

  analogReadResolution(12);

  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("Đang kết nối WiFi");

  while (WiFi.status() != WL_CONNECTED) {
    Serial.print(".");
    delay(500);
  }

  Serial.println("\nWiFi đã kết nối!");

  config.host = FIREBASE_HOST;
  config.signer.tokens.legacy_token = FIREBASE_AUTH;
  config.cert.data = NULL;

  Firebase.reconnectWiFi(true);
  Firebase.begin(&config, &auth);

  Serial.println("ESP32 Smart Agriculture Started.");
}

// ================= LOOP =================
void loop() {
  unsigned long currentMillis = millis();

  if (currentMillis - lastSendTime >= sendInterval) {
    lastSendTime = currentMillis;

    float airTemp = dht.readTemperature();
    float airHumid = dht.readHumidity();

    if (isnan(airTemp) || isnan(airHumid)) {
      Serial.println("Lỗi: Không đọc được dữ liệu từ cảm biến DHT!");
      readControlFromFirebase();

      if (!autoMode) {
        pumpRunning = manualPump;
        fanRunning = manualFan;
        writeRelay(RELAY_PUMP_PIN, pumpRunning);
        writeRelay(RELAY_FAN_PIN, fanRunning);
        sendDeviceStateToFirebase();
      }
      return;
    }

    int rawSoil = readAnalogAverage(SOIL_MOISTURE_PIN);
    int soilMoisturePercent = mapSoilToPercent(rawSoil);

    // Đọc tất cả các dữ liệu cài đặt, NPK và cả pH từ Firebase về
    readControlFromFirebase();
    
    applyControl(soilMoisturePercent, airTemp);

    // Đẩy dữ liệu DHT và độ ẩm đất thực tế lên lại Firebase
    sendSensorDataToFirebase(airTemp, airHumid, soilMoisturePercent);
    sendDeviceStateToFirebase();

    // Monitor kiểm tra kết quả nhận từ Firebase
    Serial.println("========== SMART FARM ==========");
    Serial.printf("Temp: %.1f C | Humid: %.1f %%\n", airTemp, airHumid);
    Serial.printf("Soil: %d %% | Threshold: %d %%\n", soilMoisturePercent, soilThreshold);
    Serial.printf("NPK từ Firebase: N=%d, P=%d, K=%d\n", valN, valP, valK);
    Serial.printf("pH từ Firebase: %.1f\n", soilPh); // Hiển thị pH để kiểm tra debug
    Serial.printf("Mode: %s\n", autoMode ? "AUTO" : "MANUAL");
  }
}