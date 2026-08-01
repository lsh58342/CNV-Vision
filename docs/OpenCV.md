# OpenCV

## 역할

OpenCV 계층은 카메라 프레임에 대한 **전처리**, **특징 추출·추적**, **기하학적 힌트**를 제공한다. IMU 융합 전 단계의 시각적 관측 품질을 HeatMap
가중치로 전달한다 ([HeatMap.md](./HeatMap.md)).

## 통합 방식

- Android **OpenCV SDK** (공식 또는 Maven 배포) + 필요 시 **JNI**로 커스텀 루틴.
- Kotlin/Java에서 Mat·네이티브 버퍼 수명을 엄격히 관리; 장시간 세션에서 native heap 누수 방지.

초기 로드맵에서는 Java API만으로 파이프라인 검증 후, 병목 구간만 NDK로 이전 ([Roadmap.md](./Roadmap.md)).

## 입력·출력 계약

**입력 (프레임당)**

- 그레이 또는 BGR Mat (YUV→RGB 변환은 카메라 계층 또는 OpenCV `cvtColor`)
- 타임스탬프, (선택) IMU 구간 요약 — 회전 보조

**출력**

- 추적 특징점 집합 또는 optical flow 벡터 필드
- 프레임 간 homography / essential matrix 후보 (품질 점수 포함)
- (선택) 디버그용 중간 영상: Canny, keypoint overlay

상위 `Fusion` 모듈은 OpenCV 출력의 **품질 점수**를 필수로 받는다 (inlier ratio, tracked count 등).

## 파이프라인 단계

1. **전처리** — Gaussian blur, histogram equalization(저조도), ROI crop(CAD 기준 관심 영역).
2. **특징** — ORB 우선 (속도·라이선스). 필요 시 AKAZE 등으로 교체 가능한 플러그인 형태.
3. **매칭·추적** — 이전 프레임 descriptor matching + RANSAC, 또는 Lucas–Kanade optical flow.
4. **기하** — homography 또는 5-point essential; 실패 시 “visual lost” 플래그.
5. **품질 메트릭** — inlier 수, parallax, reprojection error 요약 → HeatMap weight.

## IMU와의 관계

OpenCV 단독으로는 스케일·장기 drift에 취약하다. 설계상:

- IMU는 **프레임 간 회전 예측** 및 visual 실패 시 **dead reckoning 보조** ([IMU.md](./IMU.md)).
- OpenCV는 **translation/homography 힌트**와 **관측 신뢰도**를 제공; 절대 pose는 CAD·캘리브레이션 프레임에서 정의.

## 성능 예산

- 프레임당 목표: 33 ms @ 30 fps (기기별 relax).
- ORB feature count 상한, 이미지 피라미드 레벨, RANSAC iteration cap을 **Debug 슬라이더**로
  노출 ([Debug.md](./Debug.md)).
- 멀티스레드: `cv::parallel` 또는 전용 HandlerThread 1개; UI 스레드 호출 금지.

## 좌표·단위

- 픽셀 좌표: 분석 해상도 기준, principal point와 일치 ([Camera.md](./Camera.md)).
- Homography는 **이미지 평면 ↔ 이전 이미지 평면**; CAD 평면으로의 변환은 CAD 모듈에서 단일 경로로 합성 ([CAD.md](./CAD.md)).

## 설정 프로파일

| 프로파일     | 특징                    | 용도        |
|----------|-----------------------|-----------|
| Fast     | ORB n=500, flow 보조    | 실시간 프리뷰   |
| Balanced | ORB n=1000, RANSAC 엄격 | 일반 스캔     |
| Accurate | 특징↑, 다중 스케일           | 짧은 고정밀 구간 |

프로파일은 Application 설정에 저장; 세션 중 전환 시 tracker 상태 리셋.

## 실패·복구

- 연속 N프레임 visual lost → IMU-only propagation, HeatMap에 “low confidence” 플래그.
- 급격한 motion blur → 전처리 exposure 힌트는 카메라 AE와 연동하지 않고 품질만 낮춤 (1차).

## 관련 문서

- [Architecture.md](./Architecture.md)
- [Camera.md](./Camera.md)
- [IMU.md](./IMU.md)
- [Debug.md](./Debug.md)
