# Inspection Session + Route Cache

## 목적

STEP 10-4 — 검사 세션을 시작하고 Route/Calibration/Config를 **Freeze** 한 뒤, Event를 순서대로 기록한다.

CSV / Replay / HeatMap / CAD / AI — **미구현**

관련: [ROUTE_VALIDATION.md](./ROUTE_VALIDATION.md), [EVENT_SYSTEM.md](./EVENT_SYSTEM.md)

---

## Pipeline

```
RouteRepository
  ↓
RouteCache (RouteSnapshot)
  ↓
Inspection Start → Session Freeze
  ↓
PositionEvent / FusionEvent / CalibrationEvent / SystemEvent
  ↓
InspectionRecorder (append-only)
  ↓
Inspection End → InspectionResult → InspectionRepository
```

---

## Session Freeze

저장: Route Version/Hash, Calibration Version/Value, App Version, Timestamp, Device, Sampling Rate, **RouteQualityScore (STEP 10-3 값 재사용)**

검사 중 Snapshot 변경 금지.

---

## Route Cache

허용: Version, Hash, Snapshot, Metadata  
금지: Route 수정 / Validation / 생성

---

## Statistics

기록된 Event만으로 계산: Total Distance, Time, Shock Count, Avg/Min Confidence, Max Shock, Total Events, Route/Cal Version

---

## Package

```
inspection/
  InspectionManager, InspectionSession, InspectionRepository
  InspectionResult, InspectionStatistics, InspectionRecorder
  InspectionState, InspectionConfig, InspectionFreezeSnapshot
  RouteCache, RouteSnapshot, RouteQualityScore
```

---

## Rules

- InspectionManager만 Session 생성
- InspectionRepository만 Result 저장
- Recorder는 Event 수정 금지
- 새 Validation 수행 금지

---

## Future

STEP 11 CAD Viewer (CoordinateMapper + frozen RouteSnapshot)
