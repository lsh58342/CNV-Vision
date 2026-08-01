# Camera

## 역할

카메라 서브시스템은 **안정적인 프레임 스트림**과 **정확한 타임스탬프·내부 파라미터 메타데이터**를 제공한다. OpenCV 처리와 IMU 동기화의 입력源이다.

## API 선택

- **1차 후보: CameraX** — 프리뷰·분석 UseCase 조합, 기기별 호환성.
- **대안: Camera2** — 해상도·FPS·RAW 등 세밀 제어가 필요할 때 특정 기기 타깃용.

설계상 Camera 계층은 **어댑터 인터페이스** 뒤에 두어 CameraX↔Camera2 전환 시 상위 파이프라인을 변경하지 않는다.

## UseCase 구성

| UseCase       | 목적                                        |
|---------------|-------------------------------------------|
| Preview       | 사용자 프리뷰 (SurfaceView/TextureView/Compose) |
| ImageAnalysis | OpenCV로 넘길 분석 프레임 (YUV_420_888 권장)        |

녹화(VideoCapture)는 초기 로드맵 범위 밖. 필요 시 Debug 세션 덤프로 대체 ([Debug.md](./Debug.md)).

## 해상도·FPS

- **분석 스트림**: 처리 예산과 발열을 고려해 720p 또는 640×480 클래스에서 시작. 기기 profiler로 상향.
- **프리뷰**: 디스플레이 종횡비에 맞추되 분석 해상도와 **별도**로 두어 UI는 선명하게, 분석은 가볍게.
- **목표 FPS**: 분석 15–30 fps. IMU(100–200 Hz)와의 관계는 [IMU.md](./IMU.md) 동기화 절 참조.

## 타임스탬프

- 프레임마다 **센서/시스템 monotonic clock** 기준 타임스탬프를 부여한다.
- CameraX `ImageProxy.imageInfo.timestamp`와 IMU `SensorEvent.timestamp`의 기준(nano vs boot) 차이를 앱 기동 시
  한 번 보정(calibration offset)한다.
- 동기화 버퍼는 “프레임 시각 ± 윈도우” 내 IMU 샘플을 보간하는 방식 ([Architecture.md](./Architecture.md)).

## 내부 파라미터 (Intrinsics)

HeatMap의 이미지→월드 투영과 OpenCV 기하 연산에 필요하다.

- 초기값: CameraCharacteristics / CameraX 카메라 정보에서 focal length, principal point 추정.
- **캘리브레이션(후속)**: 체스보드 또는 CAD 마커 기반 `K`, 왜곡 계수. 결과는 세션 메타에 저장 ([CAD.md](./CAD.md)).

## 회전·좌표계

- 디스플레이 회전(`Surface.ROTATION_*`)에 따라 분석 프레임을 **OpenCV 처리 전** 일관된 “센서 좌표”로 회전한다.
- IMU 좌표계(Android 센서 축)와 카메라 optical axis 정렬은 CAD 마운트 extrinsic과 함께 정의 ([CAD.md](./CAD.md)).

## 버퍼·수명

- `ImageProxy`는 분석 콜백 종료 전까지 유효. OpenCV 네이티브로 복사하거나 zero-copy 경로가 가능할 때만 Mat이 프록시 수명을 넘지 않도록 한다.
- Ring buffer 깊이: 처리 지연 2–3 프레임분. 초과 시 **가장 오래된 프레임 폐기** (디버그에서 drop 카운트).

## 권한·수명 주기

- `CAMERA` 런타임 권한. 거부 시 제한 모드(HeatMap 비활성, 설정 안내).
- Activity/Compose lifecycle: `STARTED`에서 바인딩, `STOPPED`에서 unbind하여 배터리·발열 관리.

## 실패 모드

| 상황                 | 동작                                                                 |
|--------------------|--------------------------------------------------------------------|
| 다른 앱이 카메라 점유       | 사용자 메시지, 재시도                                                       |
| 저조도                | OpenCV 전처리 gain 힌트; HeatMap 품질 가중치 감소 ([HeatMap.md](./HeatMap.md)) |
| thermal throttling | 분석 해상도/FPS 단계적 하향                                                  |

## 관련 문서

- [Architecture.md](./Architecture.md)
- [OpenCV.md](./OpenCV.md)
- [IMU.md](./IMU.md)
