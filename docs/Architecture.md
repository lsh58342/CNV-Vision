# Architecture

## 목적

현재 단계는 **Architecture Refactoring** 반영 (기능 변경 없음). STEP 10-4 Inspection까지 유지.

---

## Composition Root

`MainActivity` = UI shell  
`MainCompositionRoot` = Feature wiring only (no algorithms)

---

## Core Events

`DistanceEvent`, `ShockEvent`, `CalibrationEvent`, `FusionEvent`, **`PositionEvent`**, `SystemEvent`  
→ Feature 간 직접 참조 대신 Core EventBus

`RouteDirection` → `core.model` (Event/Map 공유)

---

## Inspection / Route / Map / Fusion / DWG

기존 문서 유지: [INSPECTION.md](./INSPECTION.md) · [ROUTE_VALIDATION.md](./ROUTE_VALIDATION.md) · [ROUTE_GENERATOR.md](./ROUTE_GENERATOR.md) · [MAP.md](./MAP.md) · [FUSION.md](./FUSION.md) · [DWG.md](./DWG.md)

---

## Dependency Rule

허용: UI → CompositionRoot → Feature / Core  
금지: Feature → Feature 직접 호출 (Event / Repository API만)

---

## Memory

- GrayScaleFrameAnalyzer Bitmap pool + `release()`
- OpenCVManager.release() on activity stop
- Mat release in analyzer finally

---

## Future

STEP 11 CAD Viewer · STEP 12 HeatMap
