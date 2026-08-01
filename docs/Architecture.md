# Architecture

## 목적

CNV는 Android 단말의 **카메라**와 **IMU**를 동시에 사용해 공간 내에서의 관측·이동 정보를 수집하고, **OpenCV**로 영상 처리를 수행한 뒤 **CAD 기준 좌표계**에 정합하여 **HeatMap** 형태로 결과를 누적·표시하는 모바일 애플리케이션이다.

현재 단계는 **STEP 10-1 DWG Import + Conveyor Route Extraction** 까지 반영한다. Route Generator / Coordinate Mapping / CAD Viewer / HeatMap 은 미구현이다.

---

## Layer

```
Presentation → ViewModel → Repository → Core → Feature → Platform
```

Features: camera / opencv / imu / fusion / map / dwg / route / …

---

## DWG + Route Extraction (STEP 10-1)

```
dwg/   DWGReader(interface) → GeometryModel
route/ RouteExtractor → RouteCandidate (not map.Route)
```

- RouteRepository 수정 없음
- MapMatchingEngine 참조 없음
- 상세: [DWG.md](./DWG.md)

---

## Map Matching Layer (STEP 10)

FusionEvent → PositionEvent. RoutePosition에 CAD 좌표 없음. [MAP.md](./MAP.md)

---

## Fusion Layer (STEP 09)

FusionEvent only. No Position. [FUSION.md](./FUSION.md)

---

## Future Layers

| STEP | Layer | 산출 |
|------|-------|------|
| 10-2 | Route Generator + Coordinate Mapping | map.Route |
| 10-3 | Real DWG SDK Reader | Geometry from file |
| 11 | CAD Viewer | Overlay |
| 12 | HeatMap | Grid |

---

## Dependency Rule

Feature → Feature 직접 호출 금지 (EventBus / 명시된 파이프라인 입출력만).

DWGImporter는 MapMatching / RouteRepository를 건드리지 않는다.

---

## Forbidden

- MainActivity 비즈니스 로직
- 본 STEP에서 Route 생성 / Coordinate Mapping / CAD Viewer / HeatMap
- 기존 Camera / IMU / Fusion / Map Matching 수정

---

## 관련 문서

[DWG.md](./DWG.md) · [MAP.md](./MAP.md) · [FUSION.md](./FUSION.md) · [EVENT_SYSTEM.md](./EVENT_SYSTEM.md) · [DECISIONS.md.txt](./DECISIONS.md.txt)
