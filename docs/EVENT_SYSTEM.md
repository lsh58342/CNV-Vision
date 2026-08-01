# Event System

## 목적

CNV는 **Event Driven Architecture** 를 사용한다.  
Feature Module(Camera/OpenCV/IMU/CAD/…)은 서로를 직접 호출하지 않고, Core EventBus로만 통신한다.

---

## 전체 Event 구조

```
core/event/
  BaseEvent
  EventBus              (class, DI-ready)
  EventPublisher
  EventSubscriber
  EventDispatcher
  CoreEventModule       (process-wide wiring; replaceable by DI)
  DistanceEvent
  ShockEvent
  CalibrationEvent
  FusionEvent
  SystemEvent
```

`PositionEvent` 는 `map` 패키지에 두며 `BaseEvent` 를 구현한다 (Route 토폴로지 전용).

모든 Event는 **immutable data class / interface** 이다.

---

## Publisher

| Publisher | Event |
|-----------|--------|
| OpticalFlowDistanceEstimator | DistanceEvent |
| IMUProcessor / ShockDetector | ShockEvent |
| CalibrationManager | CalibrationEvent |
| FusionEngine | FusionEvent |
| MapMatchingEngine | PositionEvent |
| App / Features (optional) | SystemEvent |

발행은 `EventDispatcher.dispatch` / `EventPublisher.publish` 만 사용한다.

---

## Subscriber

| Subscriber (current / future) | Events |
|-------------------------------|--------|
| Sensor Fusion (STEP 09) | DistanceEvent, ShockEvent, CalibrationEvent |
| Map Matching (STEP 10) | FusionEvent |
| CAD / HeatMap (future) | PositionEvent (+ FusionEvent) |
| Debug tools | any |

`EventSubscriber.subscribe(Class, listener)` 로 등록한다.

---

## Life Cycle

1. Process start → `CoreEventModule.eventBus()` 생성  
2. Feature start → Publisher가 Event 발행  
3. Feature stop → 필요 시 unsubscribe / SystemEvent.FEATURE_STOPPED  
4. Tests → `CoreEventModule.resetForTests()`  

---

## Event Flow

```
Camera Frame
    → OpenCV DistanceEstimator
    → DistanceEvent ─────────────────┐
                                     ├──► FusionEngine → FusionEvent
IMU samples                          │                      ↓
    → GravityFilter / ShockDetector  │              MapMatchingEngine → PositionEvent
    → ShockEvent ────────────────────┘
Calibration session
    → CalibrationEvent ──► Fusion calibration context
```

---

## Future Expansion

- STEP 10-3 RouteLoader (JSON/DWG-derived)
- CAD / HeatMap subscribe to PositionEvent
- Replace `CoreEventModule` with Hilt/Koin providing `EventBus`

---

## Rules

- EventBus는 Kotlin `object` Singleton이 아니다 (`class EventBus`).
- Feature → Feature 직접 호출 금지.
- Camera ↔ IMU 직접 참조 금지 (Decision 006 / 007).
