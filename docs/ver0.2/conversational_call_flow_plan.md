# YeoboSay ver0.2 Conversational Call Flow Plan

## 1. 목표

ver0.2의 핵심 목표는 기존의 "녹음 버튼을 누르고 말한 뒤 업로드" 방식에서 벗어나, 사용자가 실제 전화 통화를 하듯 별도 조작 없이 AI와 대화할 수 있는 통화 흐름을 구현하는 것이다.

현재 MVP 흐름은 다음과 같다.

1. Android 앱에서 통화 세션 생성
2. 사용자가 녹음 버튼을 누름
3. 사용자가 말함
4. 녹음 종료 또는 업로드
5. 백엔드가 STT, AI 응답, TTS 처리
6. Android가 응답 음성을 재생
7. 다음 발화 때 다시 녹음 버튼을 누름

ver0.2 목표 흐름은 다음과 같다.

1. 백엔드 또는 Android 테스트 버튼으로 전화 요청 생성
2. Android 앱에 수신 화면 표시
3. 사용자가 전화 받기
4. 통화 세션 생성 및 자동 청취 시작
5. 사용자가 자연스럽게 말하면 Android가 음성 구간을 감지
6. Android가 발화 단위 오디오를 백엔드로 전송
7. 백엔드가 STT, AI 응답, TTS 처리
8. Android가 AI 음성을 자동 재생
9. 재생이 끝나면 다시 자동 청취
10. 사용자가 종료하거나 시간 제한에 도달하면 세션 종료

## 2. 설계 원칙

- 노인 사용자는 통화 중 버튼 조작을 하지 않아도 되어야 한다.
- Android는 "마이크 청취", "사용자 발화 감지", "AI 음성 재생"의 상태 전환을 명확히 관리해야 한다.
- 백엔드는 발화 단위 처리의 기준 상태를 관리하고, 같은 세션에 중복 요청이 들어와도 예측 가능하게 동작해야 한다.
- API는 ver0.1의 `call-sessions` 구조를 최대한 유지하되, 자동 통화 흐름에 필요한 상태와 이벤트를 추가한다.
- 초기 구현은 실시간 양방향 스트리밍보다 안정적인 "발화 단위 자동 업로드"를 우선한다.
- 완전한 VoIP 구현은 이번 범위가 아니다. 앱 내부 통화 UX와 백엔드 음성 처리 흐름을 전화처럼 만드는 것이 우선이다.

## 3. 범위

### 포함

- 자동 청취 기반 통화 상태 설계
- 발화 단위 음성 업로드 API 설계
- 백엔드 세션 상태 확장
- Android 자동 녹음/재생 상태머신 설계
- Socket.IO 이벤트 설계
- 오류 및 재시도 정책
- MVP 이후 확장 방향

### 제외

- 실제 휴대폰 전화 수신 `TelecomManager`/`ConnectionService` 완전 통합
- 백그라운드 장시간 통화 안정화
- 실시간 WebRTC 기반 음성 스트리밍
- 사용자 계정/인증
- 보호자 앱/알림
- 운영 모니터링 대시보드

## 4. 권장 아키텍처

ver0.2에서는 "반자동 실시간" 구조를 권장한다.

Android는 마이크를 계속 열고 있지만, 서버로 원시 오디오를 계속 스트리밍하지 않는다. 대신 Android가 무음 감지 또는 고정 청취 윈도우를 이용해 사용자의 한 발화를 잘라낸 뒤, 기존과 유사하게 발화 단위 오디오 파일을 백엔드로 업로드한다.

이 방식의 장점은 다음과 같다.

- 기존 `POST /call-sessions/:id/turns/audio` 구조를 재사용할 수 있다.
- OpenAI STT/TTS 호출 구조를 크게 바꾸지 않아도 된다.
- Android 앱의 통화 UX는 자동화되지만 백엔드 복잡도는 낮게 유지된다.
- 네트워크가 불안정해도 발화 단위 재시도가 가능하다.

향후 ver0.3에서 지연시간을 줄여야 하면 WebSocket audio chunk 또는 WebRTC 구조로 확장한다.

## 5. 사용자 흐름

### 5.1 테스트 전화 요청

1. Android 첫 화면에서 `테스트 전화 요청` 버튼 선택
2. Android가 `POST /call-invitations/test` 호출
3. 백엔드가 수신 전화 초대 생성
4. 백엔드가 Socket.IO `incoming_call` 이벤트 발행
5. Android가 수신 화면 표시

### 5.2 전화 받기

1. 사용자가 수신 화면에서 `받기` 선택
2. Android가 `POST /call-invitations/:id/accept` 호출
3. Android가 `POST /call-sessions` 호출
4. Android가 통화중 화면으로 전환
5. Android가 자동 청취 루프 시작

### 5.3 대화 루프

1. Android 상태가 `LISTENING`
2. 사용자가 말하기 시작
3. Android가 음성 입력 감지 후 `USER_SPEAKING`
4. 사용자가 일정 시간 말하지 않으면 한 발화로 확정
5. Android가 오디오 파일 생성
6. Android가 `POST /call-sessions/:id/turns/audio` 호출
7. 백엔드가 해당 발화를 `PROCESSING`으로 처리
8. 백엔드가 STT 결과, AI 답변, TTS 오디오 생성
9. Android가 응답을 수신하고 `AI_PLAYING`
10. Android가 AI 음성 재생
11. 재생 종료 후 다시 `LISTENING`

### 5.4 통화 종료

1. 사용자가 `통화 종료` 버튼 선택 또는 세션 만료
2. Android가 `POST /call-sessions/:id/end` 호출
3. 백엔드가 세션 상태를 `ENDED`로 변경
4. Android가 홈 화면 또는 종료 화면으로 이동

## 6. 상태 정의

### 6.1 Android 통화 상태

| 상태 | 설명 | 다음 상태 |
| --- | --- | --- |
| `IDLE` | 앱 시작 화면 | `INCOMING`, `STARTING` |
| `INCOMING` | 전화 수신 화면 | `STARTING`, `IDLE` |
| `STARTING` | 초대 수락 및 세션 생성 중 | `LISTENING`, `ERROR` |
| `LISTENING` | 사용자 발화를 기다리는 상태 | `USER_SPEAKING`, `ENDING` |
| `USER_SPEAKING` | 사용자가 말하는 중 | `UPLOADING`, `LISTENING` |
| `UPLOADING` | 발화 오디오 업로드 중 | `AI_PROCESSING`, `ERROR` |
| `AI_PROCESSING` | 서버 응답 대기 중 | `AI_PLAYING`, `ERROR` |
| `AI_PLAYING` | AI 음성 재생 중 | `LISTENING`, `ENDING` |
| `ERROR` | 복구 가능한 오류 | `LISTENING`, `IDLE`, `ENDING` |
| `ENDING` | 종료 요청 중 | `ENDED` |
| `ENDED` | 통화 종료 | `IDLE` |

### 6.2 백엔드 세션 상태

기존 세션 상태를 명시적으로 확장한다.

| 상태 | 설명 |
| --- | --- |
| `ACTIVE` | 통화 가능 상태 |
| `PROCESSING_TURN` | 사용자 발화를 처리 중 |
| `WAITING_FOR_USER` | 다음 사용자 발화를 기다리는 상태 |
| `ENDING` | 종료 처리 중 |
| `ENDED` | 종료됨 |
| `EXPIRED` | 만료됨 |

초기 구현에서는 DB 컬럼을 즉시 늘리지 않고, 서비스 레벨에서 상태를 계산해도 된다. 다만 중복 업로드 방지와 통화 흐름 안정화를 위해 `CallSession.status` 또는 별도 `currentTurnState` 컬럼 도입을 검토한다.

## 7. API 설계

### 7.1 기존 API 유지

기존 API는 유지한다.

```http
POST /call-invitations/test
POST /call-invitations/:id/accept
POST /call-invitations/:id/decline
POST /call-sessions
GET /call-sessions/:id
GET /call-sessions/:id/turns
POST /call-sessions/:id/turns/audio
POST /call-sessions/:id/end
```

### 7.2 발화 단위 업로드 API 개선

기존 `POST /call-sessions/:id/turns/audio`는 계속 사용하되, 자동 통화 흐름에 필요한 메타데이터를 추가한다.

```http
POST /call-sessions/:id/turns/audio
Content-Type: multipart/form-data
```

Form fields:

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `audio` | file | yes | 사용자 발화 오디오 |
| `clientTurnId` | string | yes | Android가 생성한 발화 고유 ID |
| `startedAt` | ISO string | no | 발화 시작 시각 |
| `endedAt` | ISO string | no | 발화 종료 시각 |
| `durationMs` | number | no | 발화 길이 |
| `mimeType` | string | no | Android 녹음 포맷 |
| `mode` | string | no | `manual` 또는 `auto` |

Response:

```json
{
  "turnId": "cm...",
  "clientTurnId": "android-turn-001",
  "sessionId": "cm...",
  "userText": "오늘은 기분이 좋아요.",
  "assistantText": "기분이 좋으셨다니 다행이에요.",
  "audioMimeType": "audio/mpeg",
  "audioBase64": "...",
  "failed": false,
  "riskFlag": false,
  "riskType": null
}
```

### 7.3 선택 API: 통화 상태 업데이트

Android 자동 상태를 백엔드 로그와 디버깅에 남기기 위해 선택적으로 상태 업데이트 API를 둔다.

```http
POST /call-sessions/:id/events
Content-Type: application/json
```

Request:

```json
{
  "type": "LISTENING_STARTED",
  "clientEventId": "event-001",
  "timestamp": "2026-05-30T10:00:00.000Z",
  "metadata": {
    "source": "android",
    "autoMode": true
  }
}
```

초기 구현에서는 필수 API가 아니다. 시연 및 디버깅 로그가 필요하면 도입한다.

### 7.4 선택 API: 세션 자동모드 생성

`POST /call-sessions` 생성 시 자동 통화 모드를 명시할 수 있다.

```json
{
  "mode": "auto_conversation",
  "source": "incoming_call",
  "callInvitationId": "cm..."
}
```

Response에는 Android가 사용할 자동 통화 설정을 포함할 수 있다.

```json
{
  "id": "cm...",
  "status": "ACTIVE",
  "mode": "auto_conversation",
  "audioPolicy": {
    "maxUtteranceMs": 12000,
    "silenceTimeoutMs": 1300,
    "minSpeechMs": 500,
    "sampleRateHz": 16000
  }
}
```

## 8. Socket.IO 이벤트 설계

### 8.1 기존 이벤트

```text
namespace: /call-invitations
event: incoming_call
```

Payload:

```json
{
  "callInvitationId": "cm...",
  "callerName": "여보세요",
  "message": "AI 안부 전화",
  "expiresAt": "2026-05-30T10:00:00.000Z"
}
```

### 8.2 선택 이벤트: 세션 상태

초기 자동 통화는 HTTP 응답 기반으로 충분하다. 다만 향후 백엔드 처리 시간이 길어질 경우 아래 이벤트를 추가할 수 있다.

```text
namespace: /call-sessions
event: turn_processing_started
event: turn_completed
event: turn_failed
event: session_ended
```

초기에는 Android가 `POST /turns/audio` 응답을 기다리는 구조로 단순하게 시작한다.

## 9. Backend 구현 계획

### 9.1 CallSessionsService 개선

필요 작업:

- `clientTurnId`를 요청에서 받아 중복 업로드를 방지한다.
- 같은 `clientTurnId`가 재요청되면 기존 결과를 반환하거나 `409 Conflict`를 반환한다.
- 세션이 종료/만료된 경우 오디오 업로드를 거절한다.
- 자동 모드 요청인 경우 로그에 `mode=auto`를 남긴다.
- 처리 중 예외가 발생하면 실패 turn을 저장하고 Android가 복구할 수 있는 오류 메시지를 반환한다.

### 9.2 Prisma 모델 검토

기존 모델을 확인한 뒤 아래 필드를 추가할지 결정한다.

`CallSession` 후보 필드:

- `mode`: `manual`, `auto_conversation`
- `status`: `active`, `ended`, `expired`
- `lastTurnAt`

`ConversationTurn` 후보 필드:

- `clientTurnId`
- `startedAt`
- `endedAt`
- `durationMs`
- `mode`
- `audioMimeType`
- `failed`
- `failureReason`

초기 구현에서 DB 마이그레이션 부담을 줄이고 싶다면 `clientTurnId`와 `mode`만 우선 추가한다.

### 9.3 오류 응답 정책

| 상황 | HTTP | Android 처리 |
| --- | --- | --- |
| 세션 없음 | 404 | 홈으로 복귀 또는 재시작 안내 |
| 세션 종료됨 | 409 | 통화 종료 화면 |
| 같은 발화 중복 | 200 또는 409 | 기존 응답 재사용 또는 무시 |
| STT 빈 결과 | 422 또는 200 failed | "다시 말씀해 주세요" 후 LISTENING |
| AI/TTS 실패 | 502 | 재시도 버튼 또는 LISTENING 복귀 |
| 파일 포맷 오류 | 400 | 녹음 포맷 점검 메시지 |

### 9.4 서버 로그

시연과 디버깅을 위해 아래 로그를 남긴다.

- 세션 생성
- 자동 모드 시작
- 사용자 발화 업로드 시작
- STT 결과
- AI 답변 생성
- TTS 생성 완료
- turn 완료
- 세션 종료
- 오류 발생

기존 데모 로그 옵션이 있다면 재사용한다.

## 10. Android 구현 계획

### 10.1 자동 청취 루프

Android는 통화중 화면 진입 시 자동으로 마이크 권한을 확인하고 녹음을 준비한다.

권장 루프:

1. `startAutoConversation()`
2. 권한 확인
3. `LISTENING` 상태 진입
4. AudioRecord 또는 MediaRecorder 시작
5. 음성 감지
6. 무음 timeout 도달 시 발화 종료
7. 녹음 파일 확정
8. 서버 업로드
9. AI 응답 음성 재생
10. 재생 완료 콜백에서 다시 `LISTENING`

### 10.2 음성 구간 감지

초기 구현은 정교한 VAD보다 단순 RMS 기반 감지를 권장한다.

기본값:

- 최소 발화 길이: `500ms`
- 무음 종료 기준: `1200ms ~ 1500ms`
- 최대 발화 길이: `12000ms`
- 입력 샘플레이트: 가능하면 `16000Hz`

Android 에뮬레이터에서는 마이크 입력 품질이 불안정할 수 있으므로 실제 기기 테스트를 병행한다.

### 10.3 마이크와 재생 충돌 방지

AI 음성을 재생하는 동안 마이크 녹음을 중지하거나 입력을 무시한다.

상태 전환:

```text
LISTENING -> USER_SPEAKING -> UPLOADING -> AI_PROCESSING -> AI_PLAYING -> LISTENING
```

중요 규칙:

- `AI_PLAYING` 중에는 사용자 발화 업로드를 하지 않는다.
- `UPLOADING` 중에는 새 녹음을 시작하지 않는다.
- `ENDING` 중에는 녹음/업로드/재생을 모두 중단한다.

### 10.4 UI 변경

통화중 화면은 녹음 버튼이 있어도 자동 모드에서는 보조 수단으로 취급한다.

권장 표시:

- `듣고 있어요`
- `말씀을 듣고 있어요`
- `답변을 준비하고 있어요`
- `여보세요가 말하고 있어요`
- `잘 안 들렸어요. 다시 말씀해 주세요`

자동 모드에서는 버튼 텍스트를 다음처럼 변경할 수 있다.

- 기존: `녹음`
- 변경 후보: `다시 말하기`, `수동 녹음`, 또는 숨김

단, 현재 UI 안정성을 위해 ver0.2 초기에는 버튼을 유지하고 자동 루프만 추가한다.

## 11. API 계약 변경 요약

`contract/` 업데이트 필요 사항:

- `POST /call-sessions` request에 `mode`, `source`, `callInvitationId` 추가
- `POST /call-sessions/:id/turns/audio` multipart fields에 `clientTurnId`, `mode`, `startedAt`, `endedAt`, `durationMs` 추가
- `VoiceTurnResponse`에 `clientTurnId` 추가
- 자동 통화 상태/오류 코드 문서화
- `call-invitations` 테스트 전화 요청 흐름 문서화

OpenAPI/Swagger 업데이트 필요 사항:

- DTO에 Swagger decorators 추가
- multipart form fields 문서화
- 자동모드 response example 추가

## 12. 구현 순서

### Phase 1: 계약 정리

1. `contract/`에 자동 통화 API 필드 추가
2. `docs/ver0.2` 문서 확정
3. Swagger DTO 초안 작성

### Phase 2: Backend

1. `POST /call-sessions`에 자동모드 옵션 추가
2. `POST /turns/audio`에 `clientTurnId`, 발화 메타데이터 처리 추가
3. 중복 turn 방지
4. 상태/오류 응답 정리
5. 로그 보강
6. 테스트 추가

### Phase 3: Android

1. 자동 통화 상태머신 추가
2. 마이크 권한/녹음 루프 정리
3. 무음 감지 기반 발화 종료
4. 자동 업로드 및 AI 응답 재생 후 재청취
5. 오류 복구 UI 추가
6. 실제 기기 테스트

### Phase 4: 통합 테스트

1. 서버 실행
2. Android 앱 실행
3. 테스트 전화 요청
4. 수신 화면 확인
5. 전화 받기
6. 버튼 조작 없이 대화 3턴 이상 진행
7. 통화 종료
8. 서버 로그와 DB turn 기록 확인

## 13. 테스트 시나리오

### 정상 흐름

- 테스트 전화 요청 후 수신 화면이 표시된다.
- 전화 받기를 누르면 통화중 화면으로 이동한다.
- 사용자가 말하면 버튼 없이 자동으로 업로드된다.
- AI 응답 음성이 자동으로 재생된다.
- 재생 후 다시 청취 상태가 된다.
- 통화 종료가 정상 처리된다.

### 무음

- 사용자가 말하지 않으면 업로드하지 않는다.
- 장시간 무음이면 "듣고 있어요" 상태를 유지한다.

### 짧은 소리

- 너무 짧은 소리는 발화로 처리하지 않는다.
- 필요하면 "다시 말씀해 주세요" 안내를 표시한다.

### STT 빈 결과

- 백엔드가 빈 전사 결과를 반환하면 Android는 다시 청취 상태로 복귀한다.
- 서버 로그에는 빈 결과가 남는다.

### 네트워크 실패

- 업로드 실패 시 1회 자동 재시도한다.
- 재시도 실패 시 오류 메시지를 표시하고 다시 청취 상태로 돌아간다.

### AI 재생 중 사용자 발화

- 초기 구현에서는 AI 재생 중 사용자 발화를 무시한다.
- 향후 barge-in 기능으로 확장할 수 있다.

## 14. 리스크와 대응

| 리스크 | 설명 | 대응 |
| --- | --- | --- |
| 에뮬레이터 마이크 품질 | STT 빈 결과가 자주 발생할 수 있음 | 실제 기기 테스트 병행 |
| 무음 감지 부정확 | 말이 끊기거나 너무 늦게 업로드될 수 있음 | timeout 값을 원격 설정 또는 상수로 관리 |
| AI 응답 지연 | 통화감이 떨어질 수 있음 | 처리중 안내, 짧은 응답 프롬프트 |
| 마이크/스피커 충돌 | AI 음성이 다시 녹음될 수 있음 | AI 재생 중 녹음 중지 |
| 중복 업로드 | 네트워크 재시도로 같은 turn이 중복 저장될 수 있음 | `clientTurnId` 도입 |
| 배터리/발열 | 계속 녹음 대기 시 부담 | 세션 시간 제한, idle timeout |

## 15. 향후 확장

### ver0.3 후보

- WebSocket 기반 audio chunk streaming
- 서버 주도 turn 상태 이벤트
- AI 응답 streaming TTS
- 사용자가 AI 말을 끊고 말하는 barge-in
- Android TelecomManager 기반 실제 전화 수신 UI
- 보호자에게 위험 감지 알림

## 16. MVP 성공 기준

ver0.2 자동 통화 MVP는 아래 조건을 만족하면 성공으로 본다.

- 사용자가 수신 전화를 받고 나면 녹음 버튼을 누르지 않아도 대화가 이어진다.
- 3턴 이상의 사용자-AI 대화가 자동으로 진행된다.
- 서버에는 각 turn의 사용자 전사, AI 답변, 실패 여부가 기록된다.
- 통화 종료가 정상 처리된다.
- 에뮬레이터와 실제 Android 기기 중 최소 하나에서 정상 시연 가능하다.
