# Architecture

## 목적

현재 단계는 **STEP 10-2 Route Generator + Coordinate Mapping** 까지 반영한다.

CAD Viewer / HeatMap / Route Validation 은 미구현이다.

---

## Layer

Presentation → ViewModel → Repository → Core → Feature → Platform

Features: camera / opencv / imu / fusion / map / dwg / route / …

---

## Route Generator (STEP 10-2)

```
RouteCandidate → RouteGenerator → Route → RouteRepository
                 └─ CoordinateMapper (World / Screen)
```

- RouteGenerator만 RouteRepository를 수정
- CoordinateMapper는 CAD / MapMatchingEngine 미참조
- 상세: [ROUTE_GENERATOR.md](./ROUTE_GENERATOR.md)

---

## DWG Extraction (STEP 10-1)

RouteCandidate까지. [DWG.md](./DWG.md)

---

## Map Matching (STEP 10)

FusionEvent → PositionEvent. [MAP.md](./MAP.md)

---

## Fusion (STEP 09)

FusionEvent only. [FUSION.md](./FUSION.md)

---

## Future

| STEP | 내용 |
|------|------|
| 10-3 | Route Validation + Debug Viewer |
| 11 | CAD Viewer (uses CoordinateMapper) |
| 12 | HeatMap |

---

## Forbidden

- MainActivity 비즈니스 로직
- RouteOptimizer가 Route를 자동 재설계
- 본 STEP CAD Viewer / HeatMap / Validation
- Camera / IMU / Fusion / DWG Import / Map Matching 코드 수정

---

## 관련 문서

[ROUTE_GENERATOR.md](./ROUTE_GENERATOR.md) · [DWG.md](./DWG.md) · [MAP.md](./MAP.md) · [DECISIONS.md.txt](./DECISIONS.md.txt)
