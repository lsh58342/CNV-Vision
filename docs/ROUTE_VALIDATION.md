# Route Validation + Debug Viewer

## 목적

STEP 10-3 — 생성된 Route가 Map Matching에 쓸 수 있는지 **검사만** 하고, 검은 배경 Debug Viewer로 확인한다.

Route 자동 수정 / CAD Viewer / HeatMap — **미구현**

관련: [ROUTE_GENERATOR.md](./ROUTE_GENERATOR.md), [MAP.md](./MAP.md)

---

## Pipeline

```
RouteRepository (read)
  ↓
RouteValidator + ValidationRule
  ↓
ValidationResult + RouteStatistics
  ↓
RouteDebugView / Renderer / Controller
  ↓
사용자 확인
```

---

## Validation

Severity: SUCCESS / WARNING / ERROR

검사: Route 존재, Node/Segment/Branch 수, 고립 Node, 미연결 Segment, 길이 0/최소 미만, 중복 ID, Self Loop, Invalid Direction, 연속성, 총 길이, Route ID

**Read Only** — Repository / Route 수정 없음

---

## Statistics (RouteAnalyzer)

Total / Average / Max / Min Segment Length, Node / Segment / Branch Count

---

## Debug Viewer

- 검은 배경 + 선분 (CAD 없음)
- 녹색=정상, 노랑=Warning, 빨강=Error, 파랑=Current Position, 자홍=Branch node
- Zoom / Pan / Node·Segment 선택 / Issue 하이라이트 / Statistics HUD

---

## Package

```
route/   RouteValidator, ValidationRule, ValidationResult, ValidationIssue,
         RouteStatistics, RouteAnalyzer, ValidationConfig, DefaultValidationRules
debug/   RouteDebugView, RouteDebugRenderer, RouteDebugController, RouteDebugConfig
```

---

## Future (STEP 10-4)

Route Cache + Inspection Session
