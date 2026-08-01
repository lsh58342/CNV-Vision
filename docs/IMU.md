# IMU

## 역할

IMU Framework는 Accelerometer / Gyroscope를 수집·가공하여 **ShockEvent** 를 EventBus에 발행한다.  
Camera / DistanceEstimator와 **직접 통신하지 않는다**. 향후 Sensor Fusion은 Event만 구독한다.

관련: [PROJECT_GUIDE.md.txt](./PROJECT_GUIDE.md.txt), [Architecture.md.txt](./Architecture.md.txt), [DECISIONS.md.txt](./DECISIONS.md.txt)

---

## IMU Pipeline

```
SensorManager (Accel / Gyro)
    ↓
IMUManager (register / unregister, callback enqueue)
    ↓
IMUProcessor (HandlerThread)
    ↓
GravityFilter (Low-pass gravity, linear accel)
    ↓
ShockDetector (Rule-based peak)
    ↓
IMURepository (latest IMUData)  +  EventBus ← ShockEvent
```

---

## Gravity 제거 방식

1. Accelerometer raw에 **Low Pass** (`IMUConfig.lowPassAlpha`) → Gravity vector  
2. `linear = highPassSmooth(raw - gravity)` (`IMUConfig.highPassAlpha`)  
3. Shock / confidence는 **Linear Acceleration magnitude** 기준  

Raw accelerometer를 shock threshold에 직접 쓰지 않는다.

---

## Shock Detection 알고리즘 (Rule Base)

1. `‖linearAccel‖ ≥ shockAccelerationThreshold` 이면 peak 시작  
2. Peak 동안 accel/gyro magnitude 최대값 추적  
3. Peak 종료 후 `duration ≥ peakDurationNs` 검증  
4. Noise floor 미만은 무시  
5. Confidence ≥ `confidenceThreshold` 일 때만 `ShockEvent` 발행  

AI / TFLite 미사용.

---

## Confidence 계산

가중 합 (0..1 clamp):

- Accel score vs `shockAccelerationThreshold` (weight 0.55)  
- Gyro score vs `shockGyroscopeThreshold` (weight 0.25)  
- Duration score vs `peakDurationNs` (weight 0.20)  

---

## Event 구조

| Producer | Event | 소비자 (현재/향후) |
|----------|-------|-------------------|
| DistanceEstimator | `DistanceEvent` | Fusion (future), Debug |
| IMU ShockDetector | `ShockEvent` | Fusion (future), HeatMap (future) |
| CalibrationManager | `CalibrationEvent` | Fusion / UI |

`EventBus` + `EventDispatcher` 만 사용. Camera ↔ IMU 직접 참조 금지.

---

## Config 구조 (`IMUConfig`)

| 필드 | 의미 |
|------|------|
| samplingPeriodUs | Sensor 샘플 주기 |
| lowPassAlpha | Gravity LPF |
| highPassAlpha | Linear smoothing |
| shockAccelerationThreshold | Peak accel (m/s²) |
| shockGyroscopeThreshold | Peak gyro (rad/s) |
| peakDurationNs | 최소 peak 유지 시간 |
| confidenceThreshold | ShockEvent 발행 하한 |
| noiseFloorLinearAccel | 노이즈 무시 |

Magic Number는 Config companion default로만 정의.

---

## 향후 Sensor Fusion 확장 계획

```
DistanceEvent ──┐
                ├──→ FusionEngine (subscribe only) → fused pose / shock map
ShockEvent    ──┘
CalibrationEvent → scale / quality context
```

Fusion은 CameraManager / IMUManager를 호출하지 않는다.  
CAD / HeatMap / CSV도 동일 Event 계약을 따른다.

---

## 성능

- Sensor callback: enqueue only  
- 처리: `cnv-imu-processor` HandlerThread  
- Bitmap 생성 없음  
- Debug HUD는 200ms 폴링 (Main Thread 짧은 문자열 갱신)

---

## 관련 문서

- [Camera.md](./Camera.md)
- [OpenCV.md](./OpenCV.md)
- [DistanceEstimation.md](./DistanceEstimation.md)
- [Debug.md](./Debug.md)
