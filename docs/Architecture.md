# Architecture

## 목적

CNV는 Android 단말의 **카메라**와 **IMU**를 동시에 사용해 공간 내에서의 관측·이동 정보를 수집하고, **OpenCV**로 영상 처리를 수행한 뒤 **CAD 기준 좌표계**에 정합하여 **HeatMap** 형태로 결과를 누적·표시하는 모바일 애플리케이션이다.

현재 단계는 **STEP 10 Route Model + Map Matching Engine** 까지 반영한다. DWG Import / CAD Viewer / HeatMap / AI는 아직 미구현이다.

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
Feature Module (camera / opencv / imu / fusion / map / …)
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

- Fusion은 **Event만** Subscribe 하고 **FusionEvent만** Publish 한다.
- **FusionResult는 Position을 가지지 않는다.**

상세: [FUSION.md](./FUSION.md)

---

## Map Matching Layer (STEP 10)

```
map/
  Route / RouteNode / RouteSegment / RouteEdge
  RouteRepository / RouteLoader (interface)
  PositionEstimator / MapMatchingEngine
  RoutePosition / PositionEvent / MapConfig
```

- `MapMatchingEngine` 은 **FusionEvent만** Subscribe, **PositionEvent만** Publish.
- CAD / DWG 를 참조하지 않는다.
- **RoutePosition은 CAD 좌표를 가지지 않는다.**

상세: [MAP.md](./MAP.md)

---

## Future Layers

| STEP | Layer | 입력 | 산출 |
|------|-------|------|------|
| 10-3 | DWG / Route.json Import | DWG/JSON | Route via RouteLoader |
| 11 | CAD Viewer | PositionEvent | CAD overlay |
| 12 | HeatMap | FusionEvent + PositionEvent | Grid accumulation |

---

## MainActivity

MainActivity는 비즈니스 로직을 구현하지 않는다. 초기화·권한·화면 연결만 수행한다.

---

## Package / Feature Rule

| Package | 책임 |
|---------|------|
| camera | CameraX only |
| opencv | Vision / DistanceEstimator only |
| imu | IMU / Shock only |
| fusion | Event-based Sensor Fusion only |
| map | Route model + Map Matching only |
| config (app) | CalibrationManager / Repository |
| ui / debug | Presentation / HUD |

Feature Module끼리는 **직접 참조하지 않는다**.

---

## Dependency Rule

**허용** `UI → ViewModel → Repository → Core → Feature`

**금지** Feature → Feature, Camera ↔ IMU, Fusion → Camera/IMU API, Map → CAD/DWG, OpenCV ↔ CAD

---

## Event Flow

```
DistanceEvent + ShockEvent → FusionEngine → FusionEvent
                                              ↓
                                    MapMatchingEngine → PositionEvent
                                              ↓
                                    CAD / HeatMap (future)
```

---

## Forbidden

- MainActivity에 거리·융합·맵매칭 계산
- Feature 간 직접 의존
- FusionResult / RoutePosition 에 CAD Position 포함
- 본 STEP에서 DWG / CAD Viewer / HeatMap 구현

---

## 관련 문서

- [MAP.md](./MAP.md) · [FUSION.md](./FUSION.md) · [EVENT_SYSTEM.md](./EVENT_SYSTEM.md)
- [DECISIONS.md.txt](./DECISIONS.md.txt) — Decision 005–009
