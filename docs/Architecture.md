# Architecture

## 목적

현재 단계는 **STEP 10-4 Route Cache + Inspection Session** 까지 반영한다.

---

## Inspection (STEP 10-4)

```
Route → RouteCache snapshot → InspectionSession freeze
EventBus → InspectionRecorder → InspectionResult
```

- RouteQualityScore = STEP 10-3 ValidationResult 매핑 (재검증 없음)
- 상세: [INSPECTION.md](./INSPECTION.md)

---

## Prior

Validation [ROUTE_VALIDATION.md](./ROUTE_VALIDATION.md) · Generator [ROUTE_GENERATOR.md](./ROUTE_GENERATOR.md) · Map [MAP.md](./MAP.md) · Fusion [FUSION.md](./FUSION.md)

---

## Future

| STEP | 내용 |
|------|------|
| 11 | CAD Viewer |
| 12 | HeatMap |

---

## Forbidden

- Inspection이 Event/Route를 수정·재계산
- 새 Route Validation 수행
- CSV / Replay / HeatMap / CAD / AI (본 STEP)
- 기존 Camera / IMU / Fusion / DWG / Generator / Validation / Map Matching 코드 수정

---

## 관련 문서

[INSPECTION.md](./INSPECTION.md) · [DECISIONS.md.txt](./DECISIONS.md.txt)
