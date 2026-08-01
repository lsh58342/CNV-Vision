# STEP UI-2 — Information Architecture & Screen Wireframe

> **범위:** 화면 구조·Navigation·Wireframe·기능 매핑만.  
> **금지:** XML 수정, UI 구현, Camera/OpenCV/IMU/Fusion/Calibration/Route/Inspection/HeatMap 알고리즘 변경.

---

## 1. Information Architecture

```
CNV Inspection Management System
│
├─ Operation (Operator / Maintenance)
│  ├─ Splash
│  ├─ Factory Select
│  ├─ Building Select
│  ├─ Floor Select
│  ├─ Zone List
│  ├─ Zone Dashboard          ← Operation 시작 허브
│  ├─ Inspection              ← 검사 실행만
│  ├─ Inspection Result
│  ├─ HeatMap Viewer          ← CAD ≥ 80%
│  ├─ Inspection History
│  └─ Settings                ← Camera / Calibration / About
│
└─ Admin / Developer (Settings 경유만)
   ├─ Developer               ← FPS / Tracking / Route / Fusion / Logs
   └─ Commissioning           ← DWG / Route / Calibration / Zone Editor
```

### 사용자 역할

| Role | 접근 화면 |
|------|-----------|
| **Operator** | Splash → … → Zone Dashboard → Inspection → Result |
| **Maintenance** | 위 + HeatMap Viewer + Inspection History |
| **Administrator** | 위 + Settings → Commissioning (DWG/Route/Calibration/Zone) |
| **Developer** | 위 + Settings → Developer (Debug HUDs) |

### 설계 원칙

1. **Zone** = Inspection / HeatMap / History / CSV의 최상위 단위  
2. **Inspection 화면** = 검사에 필요한 정보만 (CAD·HeatMap·Calibration·Route 편집 숨김)  
3. **CAD** = HeatMap Viewer에서만 주 화면(≥80%)  
4. **MainActivity (목표)** = Navigation Host만 (비즈니스 로직 없음)  
5. **Developer / Commissioning** = Operation Mode에서 숨김, Settings 경유

---

## 2. Navigation Diagram

```
[Splash]
   │  restore CurrentFactory / Context
   ▼
[Factory Select] ──search / recent──┐
   ▼                                │
[Building Select] ◄─────────────────┘
   ▼
[Floor Select]
   ▼
[Zone List] ──search──┐
   ▼                  │
[Zone Dashboard] ◄────┘
   │
   ├─ Start Inspection ──► [Inspection]
   │                            │ STOP / complete
   │                            ▼
   │                      [Inspection Result]
   │                            │
   │                            ├─ HeatMap ──► [HeatMap Viewer]
   │                            └─ CSV (STEP 13)
   │
   ├─ HeatMap ───────────────► [HeatMap Viewer]
   ├─ History ───────────────► [Inspection History]
   │                                ├─ open HeatMap
   │                                └─ CSV
   └─ Settings ──────────────► [Settings]
                                    ├─ Camera
                                    ├─ Calibration ──► (기존 CalibrationActivity)
                                    ├─ About
                                    ├─ Developer ──► [Developer]   (Admin/Dev only)
                                    └─ Commissioning ► [Commissioning] (Admin only)
                                                          ├─ DWG
                                                          ├─ Route
                                                          ├─ Calibration
                                                          ├─ Zone Editor
                                                          └─ Route Lock
```

### Back 규칙 (목표)

- Select 계열: 상위 Select로 pop  
- Zone Dashboard: Zone List  
- Inspection: 확인 후 Zone Dashboard (진행 중이면 STOP 유도)  
- Result / HeatMap / History: Zone Dashboard  
- Developer / Commissioning: Settings → Operation 복귀 시 Mode=OPERATION

---

## 3. Screen 목록

| # | Screen ID | 이름 | Mode | 구현 상태 (UI-2) |
|---|-----------|------|------|------------------|
| 1 | `Splash` | Splash | Both | 설계만 |
| 2 | `FactorySelect` | Factory Select | Operation | UI-1 골격 있음 |
| 3 | `BuildingSelect` | Building Select | Operation | UI-1 골격 있음 |
| 4 | `FloorSelect` | Floor Select | Operation | UI-1 골격 있음 |
| 5 | `ZoneList` | Zone List | Operation | UI-1 골격 있음 |
| 6 | `ZoneDashboard` | Zone Dashboard | Operation | UI-1 골격 있음 |
| 7 | `Inspection` | Inspection | Operation | **재설계 대상** (현 MainActivity 혼재) |
| 8 | `InspectionResult` | Inspection Result | Operation | 설계만 |
| 9 | `HeatMapViewer` | HeatMap Viewer | Operation | **재설계 대상** |
| 10 | `InspectionHistory` | Inspection History | Operation | 설계만 |
| 11 | `Settings` | Settings | Operation | UI-1 골격 있음 |
| 12 | `Developer` | Developer | Hidden in Operation | 설계만 |
| 13 | `Commissioning` | Commissioning | Admin only | UI-1 골격 있음 |

---

## 4. Screen별 역할

### Screen 1 — Splash
- 앱 초기화, seed/catalog 로드, Current Context 복원  
- 자동 이동: 최근 Factory 있으면 FactorySelect(또는 Building), 없으면 FactorySelect

### Screen 2 — Factory Select
- Factory 목록, 최근 Factory, 검색  
- 선택 → CurrentFactory 설정 → Building Select

### Screen 3 — Building Select
- CurrentFactory의 Building 목록 (WA1/WA2/…)  
- 선택 → CurrentBuilding

### Screen 4 — Floor Select
- CurrentBuilding의 Floor 목록 (1F/2F/…)  
- 선택 → CurrentFloor (+ Route 바인딩은 Commissioning 결과 사용)

### Screen 5 — Zone List
- Zone 이름·색상·최근 Inspection 요약, 검색  
- 선택 → CurrentZone → Zone Dashboard

### Screen 6 — Zone Dashboard
- Zone 정보, DWG/Calibration 상태, 최근 Inspection, History 진입, HeatMap, CSV, **Inspection Start**  
- Operation의 실질 시작 화면

### Screen 7 — Inspection
- Camera Preview, Tracking Status, Distance, Shock Count, Elapsed, START/STOP  
- **표시 금지:** HeatMap, CAD 편집, Calibration, Route 수정, Debug HUD 전부

### Screen 8 — Inspection Result
- Summary: Distance, Duration, Shock Count, Coverage  
- 액션: HeatMap 열기, CSV Export (STEP 13)

### Screen 9 — HeatMap Viewer
- CAD ≥ 80% + HeatMap Overlay  
- Timeline, Session, Statistics, Filter  
- CAD Search / Zoom / Layers / Theme / Goto Pos

### Screen 10 — Inspection History
- 세션 목록: 날짜·시간·Duration·Shock·Coverage  
- 행 액션: HeatMap, CSV

### Screen 11 — Settings
- Camera, Calibration, Developer 진입, About  
- Operator는 Developer/Commissioning 숨김 또는 disabled

### Screen 12 — Developer
- FPS, Tracking, Route, Fusion, Logs, Debug HUD 모음  
- Operation Mode에서 메뉴 숨김

### Screen 13 — Commissioning
- DWG, Route, Calibration, Zone Editor, Route Lock  
- Operation에서 접근 불가

---

## 5. Text Wireframes

### Splash
```
------------------------
Splash
------------------------
CNV Logo / Title
Initializing…
Restore Current Factory
------------------------
```

### Factory Select
```
------------------------
Factory Select
------------------------
[ Search____________ ]
Recent: Demo Factory
------------------------
• Demo Factory
• (other factories)
------------------------
            [Settings]
------------------------
```

### Building Select
```
------------------------
Building Select
------------------------
Factory: Demo Factory
------------------------
• WA1
• WA2
• WA3
------------------------
[Back]
------------------------
```

### Floor Select
```
------------------------
Floor Select
------------------------
Building: WA1
------------------------
• 1F
• 2F
• 3F
------------------------
[Back]
------------------------
```

### Zone List
```
------------------------
Zone List
------------------------
Floor: WA1 / 1F
[ Search____________ ]
------------------------
■ Zone A   last: 12:01
■ Zone B   last: —
------------------------
[Back]
------------------------
```

### Zone Dashboard
```
------------------------
Zone Dashboard
------------------------
Zone: Conveyor Zone A
Color: Red
Route: route-demo-1
------------------------
DWG: OK
Calibration: OK
Last Inspection: ab12cd34
History: 3
HeatMap refs: 2
------------------------
[ Start Inspection ]
[ HeatMap ]
[ History ]
[ CSV Export ]
------------------------
[Settings]  [Back]
------------------------
```

### Inspection
```
------------------------
Inspection
------------------------
┌──────────────────────┐
│   Camera Preview     │
└──────────────────────┘
Tracking: LOCKED / LOST
Distance: 1234 mm
Shock Count: 3
Elapsed: 00:12:05
------------------------
[ START ]    [ STOP ]
------------------------
( no CAD / no HeatMap /
  no Calibration / no Route edit )
------------------------
```

### Inspection Result
```
------------------------
Inspection Result
------------------------
Session: ab12cd34
Distance: 45000 mm
Duration: 00:18:22
Shock Count: 12
Coverage: 86%
------------------------
[ Open HeatMap ]
[ CSV Export ]
[ Back to Zone ]
------------------------
```

### HeatMap Viewer
```
------------------------
HeatMap Viewer
------------------------
┌──────────────────────┐
│                      │
│   CAD (≥80% area)    │
│   + HeatMap Overlay  │
│                      │
└──────────────────────┘
Timeline |========----|
Session / Stats
Filter: Shock Conf Seg
[Layers][Search][Goto]
------------------------
```

### Inspection History
```
------------------------
Inspection History
------------------------
Zone: Conveyor Zone A
------------------------
2026-08-01 14:02  18m  shock12  cov86%  [HM][CSV]
2026-07-30 09:11  12m  shock 4  cov71%  [HM][CSV]
------------------------
[Back]
------------------------
```

### Settings
```
------------------------
Settings
------------------------
Role: Operator | Admin | Dev
------------------------
[ Camera ]
[ Calibration ]
[ Developer ]     (Admin/Dev)
[ Commissioning ] (Admin/Dev)
[ About ]
------------------------
[Back]
------------------------
```

### Developer
```
------------------------
Developer
------------------------
FPS / Pipeline Perf
IMU Debug
Fusion Debug
Map / Position Debug
DWG Debug
Route Gen / Validation
Inspection Debug
Route Debug View Zoom
OpenCV Gray Preview toggle
------------------------
[Back to Settings]
------------------------
```

### Commissioning
```
------------------------
Commissioning
------------------------
1. Register DWG
2. Generate Route
3. Calibration
4. Zone Editor
5. Route Lock
------------------------
[ Leave → Operation ]
------------------------
```

---

## 6. 현재 MainActivity 기능 → 신규 Screen 매핑

| 현재 MainActivity (`activity_main`) 요소 | 이동 대상 Screen | 비고 |
|------------------------------------------|------------------|------|
| `preview_view` (CameraX) | **Inspection** | 검사 중 필수 |
| `opencv_gray_view` | **Developer** | Operation 숨김 |
| `button_inspection_start` / `stop` | **Inspection** | START/STOP |
| `inspection_debug_hud` | **Developer** (+ Result 요약은 Result 화면) | 검사 중 상세 HUD 제거 |
| Distance / Shock (Fusion·Map에서 파생 표시) | **Inspection** (요약 수치만) | 전체 Fusion HUD는 Developer |
| `imu_debug_hud` | **Developer** | |
| `fusion_debug_hud` | **Developer** | |
| `pipeline_perf_debug_hud` | **Developer** | FPS |
| `map_debug_hud` | **Developer** | Tracking |
| `dwg_debug_hud` | **Developer** / Commissioning 상태 | 등록은 Commissioning |
| `route_gen_debug_hud` | **Developer** | Route 생성은 Commissioning |
| `route_validation_hud` / `route_issues_hud` | **Developer** / Commissioning | |
| `route_debug_view` + zoom | **Developer** | |
| `cad_view` / `cad_container` | **HeatMap Viewer** | Inspection에서 제거 |
| `cad_debug_hud` / `cad_selection_info` | **HeatMap Viewer** (경량) / Developer (상세) | |
| CAD Search / Goto / Layers / Zoom / Fit / Reset / Theme | **HeatMap Viewer** | |
| `heatmap_overlay` | **HeatMap Viewer** | |
| `heatmap_debug_hud` / filter panel / timeline / shock toggle | **HeatMap Viewer** | |
| `button_open_calibration` | **Settings → Calibration** | Inspection 중 비표시 |
| DWG import 트리거(파이프라인) | **Commissioning** | |
| Route generate / lock | **Commissioning** | |
| Zone 관련 (현재 없음 → UI-1) | Zone List / Dashboard / Editor | |

### MainActivity 목표 역할 (이후 STEP)

```
MainActivity = Navigation Host (Fragment/Activity container)
  - 비즈니스 로직 없음
  - CompositionRoot는 Screen별 Feature Host로 분리
```

현재 `MainActivity` + `MainCompositionRoot`는 **과도기적으로 Inspection+HeatMap+Developer 혼재**.  
UI-2는 분리 설계만 확정하고, 실제 화면 분리는 후속 UI STEP에서 수행.

---

## 7. Operation / Commissioning 구분

| 영역 | Operation | Commissioning |
|------|-----------|---------------|
| Factory~Zone 탐색 | O | O (컨텍스트 설정용) |
| Inspection Start | O | X (검사 목적이 아님) |
| HeatMap / History / CSV | O (Maintenance+) | 참조만 |
| DWG / Route 생성·수정 | X | O |
| Calibration 수행 | Settings에서 가능하나 Zone 연계는 Admin | O (워크플로 필수) |
| Zone Editor | X | O |
| Route Lock | X | O |
| Developer HUD | X (숨김) | Settings→Developer |

---

## 8. Developer 기능 이동표

| Developer 기능 | 현재 위치 | 이동 후 |
|----------------|-----------|---------|
| FPS / Pipeline Perf | MainActivity HUD | Developer |
| IMU Debug | MainActivity | Developer |
| Fusion Debug | MainActivity | Developer |
| Map / Tracking Debug | MainActivity | Developer |
| DWG Debug | MainActivity | Developer (+ Commissioning 상태 배지) |
| Route Gen Debug | MainActivity | Developer |
| Route Validation / Issues | MainActivity | Developer / Commissioning |
| Route Debug View | MainActivity | Developer |
| OpenCV Gray Preview | MainActivity full-bleed | Developer toggle |
| Inspection Debug HUD | MainActivity | Developer |
| CAD Debug (상세) | MainActivity | Developer |
| HeatMap Debug (상세) | MainActivity | HeatMap Viewer 경량 + Developer 상세 |

---

## 9. 후속 STEP 힌트 (구현 금지 — 참고만)

- UI-3: Inspection 전용 Screen 분리 (MainActivity 슬림화)  
- UI-4: HeatMap Viewer 전용 Screen (CAD ≥80%)  
- UI-5: Result / History  
- UI-6: Developer Screen 집약  
- STEP 13: CSV는 Result / History / Zone Dashboard에서만

---

## 10. Build / Architecture

- 본 STEP은 **문서만** 추가. XML·알고리즘 코드 변경 없음 → Build Success 유지.  
- Architecture Rules: Zone 중심, Inspection 화면 최소 UI, CAD는 HeatMap, MainActivity=Host(목표), Developer/Commissioning은 Settings 게이트.
