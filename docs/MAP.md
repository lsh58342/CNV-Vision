# Route Model + Map Matching

## 목적

STEP 10 — `FusionEvent` 의 이동 거리를 Route 토폴로지 위에 올려 `RoutePosition` / `PositionEvent` 를 만든다.

CAD 좌표·DWG Import·HeatMap 은 본 STEP 범위 밖이다.

관련: [FUSION.md](./FUSION.md), [EVENT_SYSTEM.md](./EVENT_SYSTEM.md), [Architecture.md](./Architecture.md)

---

## Route Model

```
Route
  ├── RouteNode     (START / END / JUNCTION / WAYPOINT)
  ├── RouteSegment  (from → to, lengthMm)
  └── RouteEdge     (node 연결 + segmentId, preferred branch)
```

- **Route**: Conveyor 전체 그래프
- **Node**: 시작/종료/분기
- **Segment**: Node 사이 Conveyor 구간
- **Edge**: 분기 시 다음 Segment 선택 정보

CAD / DWG 좌표는 포함하지 않는다.

---

## RoutePosition

| 필드 | 의미 |
|------|------|
| segmentId | 현재 Segment |
| nodeId | 근접 Node (nodeRadius 기준) |
| distanceFromSegmentStart | Segment 시작부터 mm |
| progress | 0..1 |
| direction | FORWARD / BACKWARD |
| timestamp | ns |
| confidence | Fusion confidence 전달 |

**CAD 좌표 없음.**

---

## Map Matching Pipeline

```
FusionEvent (distance, timestamp, confidence)
    ↓
MapMatchingEngine
    ↓
PositionEstimator (Rule Base)
    ↓
RoutePosition
    ↓
PositionEvent → EventBus
    ↓
STEP 10-3 DWG / Route.json (future)
STEP 11 CAD Viewer (future)
STEP 12 HeatMap (future)
```

---

## Matching Rule

1. Confidence ≥ `MapConfig.minimumConfidence`
2. `|distance|` 만큼 현재 Segment 진행
3. Segment 끝 도달 시 `preferred` Edge 로 다음 Segment
4. Branch / Node 판정에 `branchToleranceMm`, `nodeRadiusMm` 사용
5. AI / GPS / PLC / AGV / 제조사 API **금지**

---

## Repository / Loader

- `RouteRepository` — 메모리만
- `RouteLoader` — **인터페이스만** (JSON/DWG 미구현)
- `InMemoryDemoRouteFactory` — 디버그용 선형 데모 Route (Import 아님)

---

## Package

```
map/
  Route.kt, RouteNode.kt, RouteSegment.kt, RouteEdge.kt
  RoutePosition.kt, RouteDirection.kt, RouteNodeType.kt
  RouteRepository.kt, RouteLoader.kt
  PositionEstimator.kt, MapMatchingEngine.kt
  PositionEvent.kt, MapConfig.kt
  InMemoryDemoRouteFactory.kt
```

---

## Future (STEP 10-3)

`RouteLoader` 구현체가 Route.json / DWG 파생 토폴로지를 로드 → `RouteRepository.setRoute`.
