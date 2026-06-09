#include <WiFi.h>
#include <DHT.h>
#include <FirebaseESP32.h>
#include "secrets.h"

// ================= SENSOR =================
#define DHTPIN 23
#define DHTTYPE DHT11

#define SOIL_MOISTURE_PIN 34
#define POT_N_PIN 35
#define POT_P_PIN 32
#define POT_K_PIN 33

// ================= RELAY =================
#define RELAY_PUMP_PIN 26
#define RELAY_FAN_PIN 27

// Nhiều relay module kích LOW.
// Nếu relay chạy ngược thì đổi true -> false.
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

// ================= NPK =================
const int NPK_MAX_VALUE = 200;

// ================= SOIL CALIBRATION =================
// Chỉnh lại sau khi xem Serial Monitor.
const int SOIL_DRY_RAW = 3200;
const int SOIL_WET_RAW = 1500;

// ================= CONTROL =================
bool autoMode = true;
bool manualPump = false;
bool manualFan = false;

int soilThreshold = 40;
float tempThreshold = 35.0;

bool pumpRunning = false;
bool fanRunning = false;

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

int mapNpkValue(int rawValue) {
  int value = map(rawValue, 0, 4095, 0, NPK_MAX_VALUE);
  return constrain(value, 0, NPK_MAX_VALUE);
}

// ================= FIREBASE CONTROL =================
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
}

// ================= APPLY CONTROL =================
void applyControl(int soilMoisturePercent, float airTemp) {
  if (autoMode) {
    // AUTO: đất khô hơn ngưỡng thì bật relay tưới
    pumpRunning = soilMoisturePercent < soilThreshold;

    // Relay 2 tạm dùng cho quạt theo nhiệt độ
    fanRunning = airTemp > tempThreshold;
  } else {
    // MANUAL: app điều khiển trực tiếp
    pumpRunning = manualPump;
    fanRunning = manualFan;
  }

  writeRelay(RELAY_PUMP_PIN, pumpRunning);
  writeRelay(RELAY_FAN_PIN, fanRunning);
}

// ================= SEND FIREBASE =================
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

  // Thêm 2 key debug để nhìn trực tiếp trên Firebase
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
  Serial.print("IP: ");
  Serial.println(WiFi.localIP());

  config.host = FIREBASE_HOST;
  config.signer.tokens.legacy_token = FIREBASE_AUTH;
  config.cert.data = NULL;

  Firebase.reconnectWiFi(true);
  Firebase.begin(&config, &auth);

  // Chỉ tạo control mặc định nếu bạn muốn.
  // Lưu ý: đoạn này sẽ ghi đè control mỗi lần ESP32 reset.
  // Nếu app đang set pump=true mà ESP32 reset, nó sẽ set lại false.
  /*
  Firebase.setBool(firebaseDataWrite, "/smart_agriculture/control/auto_mode", true);
  Firebase.setBool(firebaseDataWrite, "/smart_agriculture/control/pump", false);
  Firebase.setBool(firebaseDataWrite, "/smart_agriculture/control/fan", false);
  Firebase.setInt(firebaseDataWrite, "/smart_agriculture/control/soil_threshold", 40);
  Firebase.setFloat(firebaseDataWrite, "/smart_agriculture/control/temp_threshold", 35.0);
  */

  Serial.println("ESP32 Smart Agriculture with Relay started.");
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

      // Vẫn đọc control để test relay manual kể cả DHT lỗi
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

    int rawN = readAnalogAverage(POT_N_PIN);
    int rawP = readAnalogAverage(POT_P_PIN);
    int rawK = readAnalogAverage(POT_K_PIN);

    int valN = mapNpkValue(rawN);
    int valP = mapNpkValue(rawP);
    int valK = mapNpkValue(rawK);

    readControlFromFirebase();
    applyControl(soilMoisturePercent, airTemp);

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