# DWG Import + Conveyor Route Extraction

## 목적

STEP 10-1 — DWG를 읽어 Internal Geometry를 만들고, 선택 Layer에서 **RouteCandidate**(Center Line)까지 추출한다.

- Route Generator / Coordinate Mapping / RouteRepository 저장 — **미구현** (STEP 10-2)
- CAD Viewer / HeatMap — **미구현**

관련: [MAP.md](./MAP.md), [Architecture.md](./Architecture.md), [DECISIONS.md.txt](./DECISIONS.md.txt)

---

## Pipeline

```
DWG
  ↓
DWGReader (interface; Stub / future ODA·LibreDWG)
  ↓
GeometryExtractor + Layer Parsing
  ↓
GeometryModel
  ↓
Conveyor Layer 선택 (DWGConfig.layerFilter)
  ↓
Polyline Merge
  ↓
Center Line Extraction
  ↓
RouteCandidate
```

---

## Geometry 구조

```
GeometryModel
  ├── DWGLayer
  ├── PolylineModel / LineModel
  ├── ArcModel / CircleModel
  ├── TextModel
  └── BlockModel
```

좌표는 DWG 단위 `Point2d` 이다. Route 토폴로지 매핑은 STEP 10-2.

---

## RouteCandidate 생성

1. Layer 필터로 Polyline(+ Line→Polyline) 수집  
2. `mergeTolerance` 로 endpoint merge  
3. 평행 쌍이면 midpoint Center Line, 아니면 단일 stroke  
4. `minimumPolylineLength` 미만 폐기  
5. `RouteCandidate` 리스트 반환 — **map.Route 생성 없음**

Rule Base only. AI 금지.

---

## Package

```
dwg/
  DWGImporter, DWGReader, StubDWGReader
  DWGLayer, GeometryExtractor, GeometryModel
  PolylineModel, LineModel, ArcModel, CircleModel, TextModel, BlockModel
  DWGConfig, Point2d

route/
  RouteExtractor, CenterLineExtractor, RouteCandidate
```

---

## Rules

- `DWGImporter` → MapMatchingEngine / RouteRepository **참조·수정 금지**
- `DWGReader` 는 인터페이스; Stub은 교체용
- MainActivity는 wiring만

---

## Future (STEP 10-2)

RouteCandidate → Route Generator + Coordinate Mapping → Route → RouteRepository
