# Route Generator + Coordinate Mapping

## 목적

STEP 10-2 — `RouteCandidate` → `map.Route` 생성 및 World/Screen 좌표 변환 체계 구축.

CAD Viewer / Route Validation / HeatMap 은 미구현.

관련: [DWG.md](./DWG.md), [MAP.md](./MAP.md), [Architecture.md](./Architecture.md)

---

## Pipeline

```
RouteCandidate
  ↓
RouteBuilder
  ↓
RouteOptimizer (Normalization only)
  ↓
Route + SegmentGeometry
  ↓
CoordinateMapper
  ↓
RouteRepository.setRoute (RouteGenerator only)
  ↓
MapMatchingEngine (기존, 수정 없음)
```

---

## Route 생성

1. Center-line 점열 → Node / Segment / Edge  
2. `snapTolerance` 내 동일 좌표 Node 재사용 (분기 가능)  
3. START / END / WAYPOINT / JUNCTION 부여  
4. Segment length = world 거리 (mm 스케일)

입력은 **RouteCandidate만**. DWGImporter 직접 참조 금지.

---

## RouteOptimizer (정규화만)

허용: 동일 좌표 Node 병합, 짧은 Segment 제거, Direction/길이 정규화, ID 재정렬  
금지: 자동 재계산, Branch 자동 생성, AI 수정, Segment 자동 연결(신규)

---

## Coordinate Mapping

```
RoutePosition.progress
  → WorldCoordinate (segment lerp)
  → ScreenCoordinate (scale + offset)
```

CAD 미참조. 향후 CAD Viewer가 동일 Mapper 사용.

---

## Package

```
route/
  RouteGenerator, RouteBuilder, RouteOptimizer
  CoordinateMapper, CoordinateTransformer
  WorldCoordinate, ScreenCoordinate
  RouteImportResult, RouteConfig, CoordinateConfig
```

---

## Future (STEP 10-3)

Route Validation + Route Debug Viewer
