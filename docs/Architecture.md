# Architecture

## 목적

현재 단계는 **STEP 10-3 Route Validation + Route Debug Viewer** 까지 반영한다.

---

## Route Validation (STEP 10-3)

```
RouteRepository → RouteValidator → ValidationResult → RouteDebugViewer
```

- Validation / Debug Viewer 는 Route를 **수정하지 않음**
- MapMatchingEngine 과 상호 참조·수정 없음 (Position은 read-only 표시)
- 상세: [ROUTE_VALIDATION.md](./ROUTE_VALIDATION.md)

---

## Prior Layers

- STEP 10-2 Route Generator + Coordinate Mapping — [ROUTE_GENERATOR.md](./ROUTE_GENERATOR.md)
- STEP 10-1 DWG → RouteCandidate — [DWG.md](./DWG.md)
- STEP 10 Map Matching — [MAP.md](./MAP.md)
- STEP 09 Fusion — [FUSION.md](./FUSION.md)

---

## Future

| STEP | 내용 |
|------|------|
| 10-4 | Route Cache + Inspection Session |
| 11 | CAD Viewer |
| 12 | HeatMap |

---

## Forbidden

- Validation이 Route를 자동 수정
- MainActivity 비즈니스 로직
- Camera / IMU / Fusion / DWG / Route Generator / CoordinateMapper / Map Matching 코드 수정

---

## 관련 문서

[ROUTE_VALIDATION.md](./ROUTE_VALIDATION.md) · [ROUTE_GENERATOR.md](./ROUTE_GENERATOR.md) · [DECISIONS.md.txt](./DECISIONS.md.txt)
