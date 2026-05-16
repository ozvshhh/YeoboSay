# YeoboSay Android MVP ver0.1 개발기획서

## 1. 목적

Android MVP ver0.1의 목적은 사용자가 앱 안에서 AI와 음성으로 대화할 수 있는 최소 통화 경험을 구현하는 것이다.

이 버전은 실제 전화망이나 WebRTC 기반 실시간 통화를 사용하지 않는다. 사용자가 말하기 버튼을 눌러 짧은 음성을 녹음하고, Android 앱이 녹음 파일을 Backend에 업로드하면 Backend가 OpenAI API를 통해 STT, AI 응답 생성, TTS를 처리한 뒤 음성 응답을 Android에 반환한다.

ver0.1에서는 OpenAI Realtime API와 WebRTC를 직접 사용하지 않는다. 다만 화면 상태와 통화 세션 구조는 이후 실시간 통화 기능을 추가할 수 있도록 과하게 결합하지 않는다.

## 2. Android MVP 범위

### 포함하는 기능

- AI 통화 화면
- 마이크 권한 요청
- 통화 세션 생성 API 호출
- 사용자 음성 녹음
- 음성 파일 multipart 업로드
- Backend 응답 JSON 처리
- `audioBase64` 디코딩
- AI 음성 응답 재생
- 테스트용 사용자/AI 텍스트 대화 기록 표시
- 통화 종료 API 호출
- 기본 오류 표시

### 포함하지 않는 기능

- 실제 전화번호 기반 통화
- WebRTC
- OpenAI Realtime API 직접 연동
- AI 응답 중간 끊기
- 로그인
- 회원가입
- 보호자 관리
- 대화 기록 화면
- 푸시 알림
- 백그라운드 통화 유지

## 3. 화면 구성

MVP 화면은 Jetpack Compose로 구현한다. 고령 사용자가 쉽게 이해할 수 있도록 단순하게 구성한다.

필수 UI:

- 통화 시작 버튼
- 말하기 버튼
- 테스트용 대화 기록 영역
- AI 응답 재생 상태 표시
- 통화 종료 버튼
- 오류 메시지 표시 영역

통화 시작 직후에는 Backend에서 받은 첫 AI 인사말을 테스트용 대화 기록 영역에 표시하고 음성으로 재생한다.

권장 UI 원칙:

- UI는 `@Composable` 함수와 상태 객체를 중심으로 구성
- 화면 상태는 단일 `CallUiState` 계열 상태로 관리
- 버튼은 크고 명확해야 함
- 한 화면에서 주요 동작이 모두 가능해야 함
- 복잡한 설정이나 메뉴는 제외
- 상태 문구는 짧고 직접적으로 표시
- 통화 중 사용자가 다음 행동을 쉽게 알 수 있어야 함

## 4. 상태 관리

Android 통화 화면은 다음 상태를 가진다.

```text
Idle: 통화 시작 전
Starting: Backend 세션 생성 중
Ready: 사용자가 말할 수 있는 상태
Recording: 사용자 음성 녹음 중
Uploading: Backend에 음성 업로드 및 AI 응답 대기 중
Playing: AI 음성 응답 재생 중
Ending: 통화 종료 중
Error: 오류 발생
```

권장 Compose 구조:

```text
CallScreen
CallUiState
CallViewModel
CallAction
```

`CallScreen`은 상태 표시와 사용자 입력 이벤트 전달만 담당한다. 녹음, 네트워크 요청, 오디오 재생은 ViewModel 또는 별도 helper/service로 분리한다.

상태별 버튼 처리:

- `Idle`: 통화 시작 버튼 활성화
- `Starting`: 모든 주요 버튼 비활성화
- `Ready`: 말하기 버튼, 통화 종료 버튼 활성화
- `Recording`: 녹음 종료 동작 가능
- `Uploading`: 말하기 버튼 비활성화
- `Playing`: 말하기 버튼 비활성화, 통화 종료 가능
- `Ending`: 모든 주요 버튼 비활성화
- `Error`: 재시도 또는 통화 초기화 가능

최종 버전에서는 AI 음성 응답 재생 중 사용자가 말을 끊는 UX를 목표로 한다. ver0.1에서는 구현 안정성을 우선해 `Playing` 상태에서 말하기 버튼을 비활성화한다.

## 5. Android 권한

필수 권한:

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />
```

권한 처리:

- 통화 시작 전에 마이크 권한 확인
- 권한이 없으면 런타임 권한 요청
- 사용자가 권한을 거부하면 통화 시작 불가 안내
- 인터넷 권한은 Manifest에 선언

저장소 권한:

- MVP에서는 외부 저장소 권한을 사용하지 않는다.
- 녹음 파일과 응답 음성 파일은 앱 내부 cache directory에 임시 저장한다.

## 6. Backend 연결 정보

로컬 개발 base URL:

```text
Android Emulator: http://10.0.2.2:3000
실제 Android 기기: http://<Mac 로컬 IP>:3000
```

실제 기기 테스트 조건:

- Mac과 Android 기기가 같은 Wi-Fi에 연결되어야 함
- Backend 서버가 실행 중이어야 함
- macOS 방화벽이 3000번 포트를 막지 않아야 함
- PostgreSQL Docker 컨테이너가 실행 중이어야 함

base URL은 Android 빌드 설정 또는 상수 파일에 둔다. 기존 프로젝트 패턴이 없으면 `BuildConfig` 기반으로 `DEBUG` base URL을 관리한다.

## 7. API 연동

### 7.1 통화 세션 생성

요청:

```http
POST /call-sessions
```

응답 예시:

```json
{
  "id": "call-session-id",
  "status": "ACTIVE",
  "startedAt": "2026-05-16T05:00:00.000Z",
  "endedAt": null,
  "expiresAt": "2026-05-16T05:10:00.000Z"
}
```

Android 처리:

- 통화 시작 버튼 클릭 시 호출
- 성공하면 `sessionId` 저장
- Backend가 첫 인사말을 함께 반환하면 테스트용 대화 기록에 표시하고 음성 재생
- 상태를 `Ready`로 변경
- 실패하면 `Error` 상태 표시

### 7.2 음성 턴 업로드

요청:

```http
POST /call-sessions/:id/turns/audio
Content-Type: multipart/form-data
```

요청 필드:

```text
audio: 녹음 파일
```

응답 예시:

```json
{
  "callSessionId": "call-session-id",
  "userText": "오늘 기분이 조금 외로워",
  "assistantText": "그랬군요. 오늘은 어떤 일이 있으셨는지 천천히 이야기해 주셔도 괜찮아요.",
  "audioMimeType": "audio/mpeg",
  "audioBase64": "base64-encoded-audio",
  "failed": false,
  "riskFlag": true,
  "riskType": "EMOTIONAL_DISTRESS"
}
```

Android 처리:

- 녹음 종료 후 호출
- 상태를 `Uploading`으로 변경
- 성공하면 `audioBase64`를 디코딩
- 임시 오디오 파일로 저장
- AI 음성 재생
- `userText`, `assistantText`, `riskFlag`, `riskType`을 테스트용 대화 기록 영역에 표시
- `failed: true`이면 오디오 재생을 시도하지 않고 오류 상태를 표시
- 재생 완료 후 상태를 `Ready`로 변경

### 7.3 통화 종료

요청:

```http
POST /call-sessions/:id/end
```

Android 처리:

- 통화 종료 버튼 클릭 시 호출
- 녹음 중이면 녹음 중지
- 재생 중이면 재생 중지
- 성공 또는 실패와 관계없이 로컬 통화 상태 정리
- `sessionId` 초기화
- 상태를 `Idle`로 변경

### 7.4 세션 조회

요청:

```http
GET /call-sessions/:id
```

Android 처리:

- 테스트 및 디버깅용으로 현재 세션 상태 확인에 사용
- 화면 복구가 필요한 경우 현재 세션 상태를 다시 읽는 데 사용 가능

### 7.5 대화 턴 목록 조회

요청:

```http
GET /call-sessions/:id/turns
```

Android 처리:

- 테스트용 대화 기록 화면에 사용자 발화와 AI 응답 텍스트를 표시
- 통화 중 새 음성 턴 응답을 받은 뒤 필요하면 목록을 다시 조회

## 8. 음성 녹음

MVP에서는 실시간 스트리밍을 하지 않고 발화 단위 녹음 파일을 업로드한다.

권장 구현:

- `MediaRecorder` 사용
- 출력 포맷: MPEG_4
- 오디오 인코더: AAC
- 파일 확장자: `.m4a`
- 저장 위치: 앱 내부 cache directory
- Android 최소 지원 버전: `minSdk 26`

녹음 흐름:

```text
말하기 버튼 누름
→ 녹음 시작
→ 말하기 버튼 다시 누름
→ 녹음 종료
→ 파일 길이/크기 확인
→ Backend 업로드
```

제한:

- 너무 짧은 녹음은 업로드하지 않음
- 너무 긴 녹음은 제한
- MVP 권장 최대 녹음 길이: 30초
- 0.5초 미만 녹음은 업로드하지 않음
- Backend에는 `audio/mp4` MIME type으로 업로드

## 9. AI 음성 재생

Backend는 MVP에서 JSON 응답 안에 `audioBase64`를 포함한다.

재생 흐름:

```text
audioBase64 수신
→ Base64 decode
→ 앱 내부 cache directory에 임시 파일 저장
→ MediaPlayer로 재생
→ 재생 완료 시 임시 파일 정리
```

재생 중 처리:

- 말하기 버튼 비활성화
- 통화 종료 버튼은 활성화 가능
- 통화 종료 시 재생 중지 및 리소스 해제

## 10. 오류 처리

Android에서 처리할 오류:

- 마이크 권한 거부
- 녹음 실패
- 녹음 파일 생성 실패
- 네트워크 연결 실패
- Backend 400 응답
- Backend 404 응답
- Backend 409 응답
- Backend 500/502 응답
- Backend 응답의 `failed: true`
- Base64 디코딩 실패
- 오디오 재생 실패

사용자 메시지 원칙:

- 짧게 표시
- 기술적인 원인 노출 최소화
- 다시 시도할 수 있는 행동 제공

예시:

```text
마이크 권한이 필요해요.
네트워크 연결을 확인해 주세요.
응답을 만드는 중 문제가 생겼어요. 다시 말해 주세요.
```

## 11. Android 구현 작업 단위

### 작업 1: 통화 화면 UI

- Jetpack Compose 기반 `CallScreen`
- 통화 시작 버튼
- 말하기 버튼
- 통화 종료 버튼
- 상태 문구
- 오류 문구

### 작업 2: 권한 처리

- `RECORD_AUDIO` 권한 요청
- 권한 거부 상태 처리
- Manifest 권한 추가

### 작업 3: API client

- Backend base URL 설정
- `POST /call-sessions`
- `GET /call-sessions/:id`
- `GET /call-sessions/:id/turns`
- `POST /call-sessions/:id/turns/audio`
- `POST /call-sessions/:id/end`

### 작업 4: 녹음 기능

- `MediaRecorder` 기반 녹음
- 임시 파일 생성
- 녹음 시작/종료
- 녹음 실패 처리

### 작업 5: 음성 업로드

- multipart/form-data 요청 생성
- 녹음 파일 업로드
- 응답 JSON 파싱

### 작업 6: 음성 재생

- `audioBase64` 디코딩
- 임시 오디오 파일 저장
- `MediaPlayer` 재생
- 재생 종료 처리

### 작업 7: 통화 종료

- 종료 API 호출
- 로컬 리소스 해제
- 상태 초기화

## 12. Android 완료 기준

- 앱에서 통화 시작 가능
- Backend 세션 생성 성공
- 마이크 권한 요청 가능
- 음성 녹음 가능
- 녹음 파일 업로드 가능
- AI 응답 JSON 수신 가능
- AI 음성 재생 가능
- 테스트용 사용자/AI 텍스트 표시 가능
- 여러 번 대화 가능
- 통화 종료 가능
- Android 빌드 통과

검증 명령:

```bash
cd android
./gradlew build
```
