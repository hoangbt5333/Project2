#include <WiFi.h>
#include <DHT.h>
#include <FirebaseESP32.h>
#include "secrets.h"

// ===================== SENSOR PINS =====================
#define DHTPIN 23
#define DHTTYPE DHT11

#define SOIL_MOISTURE_PIN 34
#define POT_N_PIN 35
#define POT_P_PIN 32
#define POT_K_PIN 33

// ===================== RELAY PINS =====================
// Relay 1: bơm / tưới nước
// Relay 2: quạt / thiết bị phụ / test relay
#define RELAY_PUMP_PIN 26
#define RELAY_FAN_PIN 27

// Nhiều module relay 5V kích mức LOW.
// Nếu relay của bạn bị ngược logic, đổi true thành false.
const bool RELAY_ACTIVE_LOW = true;

// ===================== FIREBASE OBJECTS =====================
DHT dht(DHTPIN, DHTTYPE);

FirebaseData firebaseDataWrite;
FirebaseData firebaseDataRead;

FirebaseAuth auth;
FirebaseConfig config;

// ===================== TIMING =====================
unsigned long lastSendTime = 0;
const unsigned long sendInterval = 3000;

// ===================== NPK =====================
const int NPK_MAX_VALUE = 200;

// ===================== SOIL CALIBRATION =====================
// Bạn nên đo lại bằng Serial Monitor.
// Giá trị tham khảo:
// - Cảm biến khô: raw cao
// - Cảm biến ướt/nước: raw thấp
const int SOIL_DRY_RAW = 3200;
const int SOIL_WET_RAW = 1500;

// ===================== CONTROL DEFAULT =====================
bool autoMode = true;
bool manualPump = false;
bool manualFan = false;

int soilThreshold = 40;
float tempThreshold = 35.0;

bool pumpRunning = false;
bool fanRunning = false;

// ===================== RELAY HELPERS =====================
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

// ===================== SENSOR HELPERS =====================
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

int mapNpkValue(int rawValue) {
  int value = map(rawValue, 0, 4095, 0, NPK_MAX_VALUE);
  return constrain(value, 0, NPK_MAX_VALUE);
}

// ===================== FIREBASE CONTROL =====================
void readControlFromFirebase() {
  if (Firebase.getBool(firebaseDataRead, "/smart_agriculture/control/auto_mode")) {
    autoMode = firebaseDataRead.boolData();
  }

  if (Firebase.getBool(firebaseDataRead, "/smart_agriculture/control/pump")) {
    manualPump = firebaseDataRead.boolData();
  }

  if (Firebase.getBool(firebaseDataRead, "/smart_agriculture/control/fan")) {
    manualFan = firebaseDataRead.boolData();
  }

  if (Firebase.getInt(firebaseDataRead, "/smart_agriculture/control/soil_threshold")) {
    soilThreshold = firebaseDataRead.intData();
    soilThreshold = constrain(soilThreshold, 0, 100);
  }

  if (Firebase.getFloat(firebaseDataRead, "/smart_agriculture/control/temp_threshold")) {
    tempThreshold = firebaseDataRead.floatData();
    tempThreshold = constrain(tempThreshold, 0.0, 60.0);
  }
}

// ===================== APPLY CONTROL =====================
void applyControl(int soilMoisturePercent, float airTemp) {
  if (autoMode) {
    // Auto: đất ít nước hơn ngưỡng thì bật relay tưới
    pumpRunning = soilMoisturePercent < soilThreshold;

    // Relay 2: nếu nhiệt độ cao hơn ngưỡng thì bật
    // Nếu bạn chỉ muốn test relay 2 bằng app, có thể đổi thành: fanRunning = manualFan;
    fanRunning = airTemp > tempThreshold;
  } else {
    // Manual: app điều khiển trực tiếp
    pumpRunning = manualPump;
    fanRunning = manualFan;
  }

  writeRelay(RELAY_PUMP_PIN, pumpRunning);
  writeRelay(RELAY_FAN_PIN, fanRunning);
}

// ===================== SEND DATA =====================
void sendSensorDataToFirebase(
  float airTemp,
  float airHumid,
  int soilMoisturePercent,
  int valN,
  int valP,
  int valK
) {
  Firebase.setFloat(firebaseDataWrite, "/smart_agriculture/air_temperature", airTemp);
  Firebase.setFloat(firebaseDataWrite, "/smart_agriculture/air_humidity", airHumid);
  Firebase.setInt(firebaseDataWrite, "/smart_agriculture/soil_moisture", soilMoisturePercent);

  Firebase.setInt(firebaseDataWrite, "/smart_agriculture/npk_n", valN);
  Firebase.setInt(firebaseDataWrite, "/smart_agriculture/npk_p", valP);
  Firebase.setInt(firebaseDataWrite, "/smart_agriculture/npk_k", valK);
}

void sendDeviceStateToFirebase() {
  Firebase.setBool(firebaseDataWrite, "/smart_agriculture/state/pump_running", pumpRunning);
  Firebase.setBool(firebaseDataWrite, "/smart_agriculture/state/fan_running", fanRunning);
  Firebase.setString(firebaseDataWrite, "/smart_agriculture/state/mode", autoMode ? "AUTO" : "MANUAL");

  // Ghi thêm để debug trên Firebase
  Firebase.setBool(firebaseDataWrite, "/smart_agriculture/state/relay1", pumpRunning);
  Firebase.setBool(firebaseDataWrite, "/smart_agriculture/state/relay2", fanRunning);
}

// ===================== SETUP =====================
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
  Serial.print("IP: ");
  Serial.println(WiFi.localIP());

  config.host = FIREBASE_HOST;
  config.signer.tokens.legacy_token = FIREBASE_AUTH;
  config.cert.data = NULL;

  Firebase.reconnectWiFi(true);
  Firebase.begin(&config, &auth);

  // Tạo giá trị control mặc định nếu trên Firebase chưa có.
  Firebase.setBool(firebaseDataWrite, "/smart_agriculture/control/auto_mode", true);
  Firebase.setBool(firebaseDataWrite, "/smart_agriculture/control/pump", false);
  Firebase.setBool(firebaseDataWrite, "/smart_agriculture/control/fan", false);
  Firebase.setInt(firebaseDataWrite, "/smart_agriculture/control/soil_threshold", 40);
  Firebase.setFloat(firebaseDataWrite, "/smart_agriculture/control/temp_threshold", 35.0);

  Serial.println("ESP32 Smart Agriculture started.");
}

// ===================== LOOP =====================
void loop() {
  unsigned long currentMillis = millis();

  if (currentMillis - lastSendTime >= sendInterval) {
    lastSendTime = currentMillis;

    float airTemp = dht.readTemperature();
    float airHumid = dht.readHumidity();

    if (isnan(airTemp) || isnan(airHumid)) {
      Serial.println("Lỗi: Không đọc được dữ liệu từ cảm biến DHT!");
      return;
    }

    int rawSoil = readAnalogAverage(SOIL_MOISTURE_PIN);
    int soilMoisturePercent = mapSoilToPercent(rawSoil);

    int rawN = readAnalogAverage(POT_N_PIN);
    int rawP = readAnalogAverage(POT_P_PIN);
    int rawK = readAnalogAverage(POT_K_PIN);

    int valN = mapNpkValue(rawN);
    int valP = mapNpkValue(rawP);
    int valK = mapNpkValue(rawK);

    // Đọc lệnh từ app Android
    readControlFromFirebase();

    // Tự động hoặc thủ công điều khiển relay
    applyControl(soilMoisturePercent, airTemp);

    // Gửi sensor + trạng thái relay lên Firebase
    sendSensorDataToFirebase(
      airTemp,
      airHumid,
      soilMoisturePercent,
      valN,
      valP,
      valK
    );

    sendDeviceStateToFirebase();

    Serial.println("========== SMART FARM ==========");
    Serial.printf("Temp: %.1f C | Humid: %.1f %%\n", airTemp, airHumid);
    Serial.printf("Soil raw: %d | Soil: %d %% | Threshold: %d %%\n",
                  rawSoil, soilMoisturePercent, soilThreshold);
    Serial.printf("NPK: N=%d, P=%d, K=%d\n", valN, valP, valK);
    Serial.printf("Mode: %s\n", autoMode ? "AUTO" : "MANUAL");
    Serial.printf("Manual pump: %s | Manual fan: %s\n",
                  manualPump ? "ON" : "OFF",
                  manualFan ? "ON" : "OFF");
    Serial.printf("Relay pump: %s | Relay fan: %s\n",
                  pumpRunning ? "ON" : "OFF",
                  fanRunning ? "ON" : "OFF");
  }
}