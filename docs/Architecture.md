# Architecture

## 목적

CNV는 Android 단말의 **카메라**와 **IMU**를 동시에 사용해 공간 내에서의 관측·이동 정보를 수집하고, **OpenCV**로 영상 처리를 수행한 뒤 **CAD 기준 좌표계**에 정합하여 **HeatMap** 형태로 결과를 누적·표시하는 모바일 애플리케이션이다.

현재 단계는 **STEP 09 Sensor Fusion Engine** 까지 반영한다. Map Matching / CAD / HeatMap / AI는 아직 미구현이다.

---

## Layer

```
Presentation
    ↓
ViewModel
    ↓
Repository
    ↓
Core
    ↓
Feature Module (camera / opencv / imu / fusion / …)
    ↓
Platform
```

---

## Core Layer

```
core/
  event/      EventBus, Dispatcher, Publisher, Subscriber, *Event (+ FusionEvent)
  config/     AppConfig, CameraConfig, OpenCVConfig, IMUConfig, …
  common/     Result, State, Logger, TimeProvider, Dispatchers
  math/       MathUtil, CoordinateUtil
  model/      Shared DTOs (e.g. Vec3f)
```

공통 기능과 Event Driven 통신은 Core에서만 관리한다.

---

## Fusion Layer (STEP 09)

```
fusion/
  FusionEngine / FusionProcessor / FusionRuleEngine
  FusionRepository / FusionConfig
  FusionResult / FusionConfidence / FusionStatistics
```

- Fusion은 **Event만** Subscribe 하고 **FusionEvent만** Publish 한다.
- Camera / IMU Feature 를 직접 참조하지 않는다.
- **FusionResult는 Position을 가지지 않는다.**

상세: [FUSION.md](./FUSION.md)

---

## Future Layers

| STEP | Layer | 입력 | 산출 |
|------|-------|------|------|
| 10 | Map Matching | FusionEvent | Position / path on map |
| 11 | CAD | Map-matched pose | CAD overlay |
| 12 | HeatMap | FusionEvent + Position | Grid accumulation |

---

## MainActivity

MainActivity는 비즈니스 로직을 구현하지 않는다.

초기화·권한·화면 연결만 수행한다.

---

## Package / Feature Rule

| Package | 책임 |
|---------|------|
| camera | CameraX only |
| opencv | Vision / DistanceEstimator only |
| imu | IMU / Shock only |
| fusion | Event-based Sensor Fusion only |
| config (app) | CalibrationManager / Repository |
| ui | Presentation |
| debug | Debug HUD |

Feature Module끼리는 **직접 참조하지 않는다**.

---

## Dependency Rule

**허용**

```
UI → ViewModel → Repository → Core → Feature
```

**금지**

```
Feature → Feature
Camera → IMU
IMU → Camera
Fusion → Camera / IMU (API)
OpenCV → CAD
CAD → OpenCV
```

Core EventBus를 통해서만 모듈 간 신호가 흐른다.

---

## Event Flow

```
DistanceEstimator ──publish──► DistanceEvent ──┐
                                               ├──► FusionEngine ──► FusionEvent
IMU ShockDetector ──publish──► ShockEvent     ──┘         │
CalibrationManager ─publish──► CalibrationEvent ──────────┘
                                                          ↓
                                              Map Matching / CAD / HeatMap (future)
```

Camera와 IMU는 서로를 호출하지 않는다.

상세: [EVENT_SYSTEM.md](./EVENT_SYSTEM.md)

---

## Distance Estimator

Interface: DistanceEstimator

구현체: OpticalFlowDistanceEstimator → (future) FusionDistanceEstimator / AIDistanceEstimator

UI는 구현체를 직접 참조하지 않는다.

---

## Sensor Fusion

IMU는 Camera를 대체하지 않고 보조한다.

Fusion은 Feature API를 호출하지 않고 Event만 구독한다 (Decision 008).

---

## Calibration

Calibration 값은 Config + CalibrationManager로 관리한다.

Session 누적 Pixel 정책 (Decision 005).

---

## Thread

Camera / OpenCV / IMU / Fusion / UI / Logging 은 독립적으로 동작한다.

---

## Performance

GrayScale, ROI, Frame Skip, Object Pool, Bitmap reuse.

---

## Forbidden

- MainActivity에 OpenCV / IMU / 거리·융합 계산
- UI에서 OpenCV 직접 호출
- Feature 간 직접 의존
- FusionResult에 Position 포함

---

## 관련 문서

- [FUSION.md](./FUSION.md) — Fusion Pipeline
- [EVENT_SYSTEM.md](./EVENT_SYSTEM.md) — Event Bus / Flow
- [DECISIONS.md.txt](./DECISIONS.md.txt) — Decision 005–008
- [Camera.md](./Camera.md) · [OpenCV.md](./OpenCV.md) · [IMU.md](./IMU.md)
- [CAD.md](./CAD.md) · [HeatMap.md](./HeatMap.md) · [Roadmap.md](./Roadmap.md)
