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
4. 통화 세션 생성
5. AI가 고정 첫 인사를 재생
6. Android가 자동 청취를 시작
7. 사용자가 자연스럽게 말하면 Android가 음성 구간을 감지
8. Android가 발화 단위 오디오를 백엔드로 전송
9. 백엔드가 STT, AI 응답, TTS 처리
10. Android가 AI 음성을 자동 재생
11. AI 재생 중 사용자가 말하면 재생을 멈추고 사용자 발화를 우선 처리
12. 대화가 끝나거나 시간 제한에 도달하면 세션 종료
13. Android가 통화 종료 후 방금 한 통화 내용을 보여준다.

## 1.1 확정 요구사항

사용자 답변을 기준으로 ver0.2에서 확정된 요구사항은 다음과 같다.

- 통화 시작 직후 AI가 먼저 인사한다.
- 첫 인사 문구는 고정한다: `안녕하세요 왕송길 어르신 AI통화 서비스 세요입니다!`
- AI 질문 후 사용자가 약 3초 동안 아무 말도 하지 않으면 재촉 멘트를 재생한다. 예: `여보세요? 제 말 들리세요?`
- 최대 통화 시간은 10분이다.
- 10분이 지나면 `어르신 아쉽지만 오늘 통화는 여기까지에요.` 안내 후 종료한다.
- 기본 대화 목표는 5턴이다: 안부 인사, 건강 확인, 복약 확인, 일정 확인, 기분 확인.
- 5턴 이후 사용자가 더 대화하고 싶어 하면 10분 제한 안에서 계속 대화한다.
- 사용자가 "그만", "끊어", "먼저 끊어"와 같은 종료 의사를 말하면 AI가 마무리 안내 후 통화를 종료한다.
- 사용자는 AI가 말하는 중에도 말을 끊을 수 있어야 한다.
- AI 음성 재생 중 사용자 발화가 감지되면 AI 재생을 멈추고 사용자 발화를 처리한다.
- 통화 화면에는 대화 기록이 표시된다.
- 통화 중 버튼은 `스피커`, `통화 종료`, `블루투스` 3개로 구성한다.
- 자동 녹음 중임을 사용자에게 명확히 안내한다.
- `듣고 있어요`, `답변 준비 중` 같은 상태 문구는 디버깅용으로 준비하되 실제 서비스에서는 숨길 수 있게 한다.
- AI 음성 볼륨은 기본적으로 크게 재생한다.
- 큰글자 UI는 옵션이 아니라 기본 강제 적용이다.
- 위험 신호가 감지되면 우선 서버 로그에 남기고, 사용자에게 음성으로 위험 안내를 한다.
- 통화 종료 후 Android에서 통화 내용 확인 화면을 제공한다.
- DB 마이그레이션을 포함해 백엔드, Android, API 계약을 전반적으로 수정한다.

## 2. 설계 원칙

- 노인 사용자는 통화 중 버튼 조작을 하지 않아도 되어야 한다.
- Android는 "마이크 청취", "사용자 발화 감지", "AI 음성 재생"의 상태 전환을 명확히 관리해야 한다.
- 백엔드는 발화 단위 처리의 기준 상태를 관리하고, 같은 세션에 중복 요청이 들어와도 예측 가능하게 동작해야 한다.
- API는 ver0.1의 `call-sessions` 구조를 최대한 유지하되, 자동 통화 흐름에 필요한 상태와 이벤트를 추가한다.
- 초기 구현은 서버 실시간 스트리밍보다 안정적인 "Android 음성 감지 + 발화 단위 자동 업로드"를 우선한다.
- AI 재생 중에도 Android 마이크 청취는 유지되어야 하며, 사용자 발화가 감지되면 AI 재생을 중단한다.
- 완전한 VoIP 구현은 이번 범위가 아니다. 앱 내부 통화 UX와 백엔드 음성 처리 흐름을 전화처럼 만드는 것이 우선이다.

## 3. 범위

### 포함

- 자동 청취 기반 통화 상태 설계
- 발화 단위 음성 업로드 API 설계
- 백엔드 세션 상태 확장
- Android 자동 녹음/재생 상태머신 설계
- AI 첫 인사 및 5턴 체크 대화 흐름 설계
- AI 발화 중 사용자 끼어들기(barge-in) 설계
- 통화 종료 후 대화 내용 확인 화면 설계
- Socket.IO 이벤트 설계
- 오류 및 재시도 정책
- DB 마이그레이션 설계
- MVP 이후 확장 방향

### 제외

- 실제 휴대폰 전화 수신 `TelecomManager`/`ConnectionService` 완전 통합
- 백그라운드 장시간 통화 안정화
- 실시간 WebRTC 기반 음성 스트리밍
- 사용자 계정/인증
- 보호자 앱/알림
- 운영 모니터링 대시보드

## 4. 권장 아키텍처

ver0.2에서는 "Android 주도 자동 발화 감지 + 서버 발화 단위 처리" 구조를 권장한다.

Android는 `AudioRecord` 기반으로 마이크 입력을 계속 관찰한다. 서버로 원시 오디오를 계속 스트리밍하지는 않고, Android가 RMS 기반으로 사용자의 발화 구간을 감지해 한 발화 단위 파일로 만든 뒤 백엔드에 업로드한다.

AI 음성 재생 중에도 마이크 입력 관찰은 유지한다. 사용자가 AI 말을 끊고 말하면 Android는 AI 재생을 즉시 중단하고, 사용자 발화를 새 turn으로 처리한다. 이 요구사항 때문에 `MediaRecorder`만으로는 한계가 있을 수 있으며, 초기 구현은 `AudioRecord`를 우선 검토한다.

이 방식의 장점은 다음과 같다.

- 기존 `POST /call-sessions/:id/turns/audio` 구조를 참고하되, 자동 통화 전용 `POST /call-sessions/:id/auto-turns/audio`로 분리할 수 있다.
- OpenAI STT/TTS 호출 구조를 크게 바꾸지 않아도 된다.
- Android 앱의 통화 UX는 자동화되지만 백엔드 복잡도는 낮게 유지된다.
- 네트워크가 불안정해도 발화 단위 재시도가 가능하다.
- barge-in 요구사항을 Android 상태머신 안에서 먼저 해결할 수 있다.

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
3. Android가 `POST /call-sessions`를 `mode=auto_conversation`으로 호출
4. Android가 통화중 화면으로 전환
5. AI 첫 인사를 재생: `안녕하세요 왕송길 어르신 AI통화 서비스 세요입니다!`
6. Android가 자동 청취 루프 시작

### 5.3 대화 루프

1. Android 상태가 `LISTENING`
2. 사용자가 말하기 시작
3. Android가 음성 입력 감지 후 `USER_SPEAKING`
4. 사용자가 일정 시간 말하지 않으면 한 발화로 확정
5. Android가 오디오 파일 생성
6. Android가 `POST /call-sessions/:id/auto-turns/audio` 호출
7. 백엔드가 해당 발화를 `PROCESSING`으로 처리
8. 백엔드가 STT 결과, AI 답변, TTS 오디오 생성
9. Android가 응답을 수신하고 `AI_PLAYING`
10. Android가 AI 음성 재생
11. AI 재생 중 사용자 발화가 감지되면 `BARGE_IN_DETECTED`로 전환하고 재생을 중단
12. 재생 종료 후 다시 `LISTENING`

기본 5턴 주제 순서는 다음과 같다.

1. 안부 인사: 오늘 하루가 어떠셨는지 묻는다.
2. 건강 확인: 몸이 불편한 곳은 없는지 묻는다.
3. 복약 확인: 약은 잘 챙겨 드셨는지 묻는다.
4. 일정 확인: 오늘 또는 내일 일정이 있는지 묻는다.
5. 기분 확인: 마음 상태와 외로움 정도를 묻는다.

사용자가 단답으로 답한 경우, 질문의 목적이 충족되면 다음 주제로 넘어간다. 질문의 목적이 충족되지 않았으면 한 번만 더 쉽게 바꿔 묻고, 그래도 답이 짧으면 다음 주제로 넘어간다.

AI 질문 후 약 3초 동안 사용자 발화가 감지되지 않으면 재촉 멘트를 재생한다.

### 5.4 통화 종료

1. 사용자가 `통화 종료` 버튼 선택, 종료 의사 발화, 10분 제한 도달, 또는 세션 만료
2. Android가 `POST /call-sessions/:id/end` 호출
3. 백엔드가 세션 상태를 `ENDED`로 변경
4. AI가 가능한 경우 마무리 멘트를 재생
5. Android가 통화 종료 후 대화 내용 확인 화면으로 이동

## 6. 상태 정의

### 6.1 Android 통화 상태

| 상태 | 설명 | 다음 상태 |
| --- | --- | --- |
| `IDLE` | 앱 시작 화면 | `INCOMING`, `STARTING` |
| `INCOMING` | 전화 수신 화면 | `STARTING`, `IDLE` |
| `STARTING` | 초대 수락 및 세션 생성 중 | `AI_GREETING`, `ERROR` |
| `AI_GREETING` | 고정 첫 인사 재생 중 | `LISTENING`, `BARGE_IN_DETECTED`, `ENDING` |
| `LISTENING` | 사용자 발화를 기다리는 상태 | `USER_SPEAKING`, `NO_RESPONSE_PROMPTING`, `ENDING` |
| `NO_RESPONSE_PROMPTING` | 3초 무응답 후 재촉 멘트 재생 | `LISTENING`, `BARGE_IN_DETECTED`, `ENDING` |
| `USER_SPEAKING` | 사용자가 말하는 중 | `UPLOADING`, `LISTENING` |
| `UPLOADING` | 발화 오디오 업로드 중 | `AI_PROCESSING`, `ERROR` |
| `AI_PROCESSING` | 서버 응답 대기 중 | `AI_PLAYING`, `ERROR` |
| `AI_PLAYING` | AI 음성 재생 중 | `LISTENING`, `BARGE_IN_DETECTED`, `ENDING` |
| `BARGE_IN_DETECTED` | AI 재생 중 사용자 발화 감지, 재생 중단 | `USER_SPEAKING`, `UPLOADING` |
| `ERROR` | 복구 가능한 오류 | `LISTENING`, `IDLE`, `ENDING` |
| `ENDING` | 종료 요청 중 | `ENDED` |
| `ENDED` | 통화 종료 | `CALL_SUMMARY` |
| `CALL_SUMMARY` | 방금 한 통화 내용 확인 | `IDLE` |

### 6.2 백엔드 세션 상태

기존 세션 상태를 명시적으로 확장한다.

| 상태 | 설명 |
| --- | --- |
| `ACTIVE` | 통화 가능 상태 |
| `PROCESSING_TURN` | 사용자 발화를 처리 중 |
| `WAITING_FOR_USER` | 다음 사용자 발화를 기다리는 상태 |
| `AI_SPEAKING` | AI 응답을 생성했거나 재생 중인 상태 |
| `ENDING` | 종료 처리 중 |
| `ENDED` | 종료됨 |
| `EXPIRED` | 만료됨 |

ver0.2에서는 DB 마이그레이션을 포함해 `CallSession.status`, `CallSession.mode`, `ConversationTurn.status`, `ConversationTurn.clientTurnId`를 명시적으로 저장한다.

### 6.3 백엔드 turn 상태

| 상태 | 설명 |
| --- | --- |
| `UPLOADED` | Android가 발화 오디오를 업로드함 |
| `TRANSCRIBING` | STT 처리 중 |
| `TRANSCRIBED` | STT 완료 |
| `RESPONDING` | AI 답변 생성 중 |
| `RESPONDED` | AI 답변 생성 완료 |
| `SYNTHESIZING` | TTS 생성 중 |
| `COMPLETED` | turn 전체 처리 완료 |
| `FAILED_STT` | STT 실패 |
| `FAILED_LLM` | AI 답변 생성 실패 |
| `FAILED_TTS` | TTS 생성 실패 |
| `FAILED_UNKNOWN` | 기타 실패 |

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
POST /call-sessions/:id/auto-turns/audio
POST /call-sessions/:id/events
POST /call-sessions/:id/end
```

### 7.2 자동 통화 전용 발화 업로드 API

기존 `POST /call-sessions/:id/turns/audio`는 수동 녹음 호환용으로 유지한다. 자동 통화는 전용 endpoint를 둔다.

```http
POST /call-sessions/:id/auto-turns/audio
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
| `mode` | string | yes | `auto_conversation` |
| `bargeIn` | boolean | no | AI 재생 중 끼어들기로 생성된 발화 여부 |
| `conversationStep` | string | no | `greeting`, `health`, `medication`, `schedule`, `mood`, `free_talk` |

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
  "conversationStep": "health",
  "nextAction": "play_audio",
  "failed": false,
  "riskFlag": false,
  "riskType": null
}
```

`nextAction` 후보:

| 값 | 설명 |
| --- | --- |
| `play_audio` | AI 음성을 재생한 뒤 다시 청취 |
| `listen_again` | STT가 비었거나 발화가 불명확해 다시 청취 |
| `end_call_after_audio` | 마무리 멘트 재생 후 통화 종료 |
| `force_end` | 즉시 통화 종료 |

### 7.3 통화 상태 업데이트 API

Android 자동 상태를 백엔드 로그와 디버깅에 남기기 위해 상태 업데이트 API를 둔다. 이 API는 운영 필수 기능은 아니지만 ver0.2 개발/시연에서는 사용한다.

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

상태 문구는 실제 서비스 UI에서는 숨길 수 있지만, 서버 로그와 통합 테스트에서는 유지한다.

### 7.4 세션 자동모드 생성

`POST /call-sessions` 생성 시 자동 통화 모드를 명시할 수 있다.

```json
{
  "mode": "auto_conversation",
  "source": "incoming_call",
  "callInvitationId": "cm..."
}
```

Response에는 Android가 사용할 자동 통화 설정을 포함한다.

```json
{
  "id": "cm...",
  "status": "ACTIVE",
  "mode": "auto_conversation",
  "audioPolicy": {
    "maxUtteranceMs": 12000,
    "speechEndSilenceMs": 1300,
    "noResponsePromptMs": 3000,
    "minSpeechMs": 500,
    "sampleRateHz": 16000,
    "preferredFormat": "audio/mp4"
  },
  "conversationPolicy": {
    "maxDurationMs": 600000,
    "targetTurnCount": 5,
    "firstGreetingText": "안녕하세요 왕송길 어르신 AI통화 서비스 세요입니다!"
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

### 8.2 통화 세션 이벤트

수신 전화 이벤트와 통화 세션 이벤트는 namespace를 분리한다. Android는 HTTP 응답을 기다리면서도 Socket.IO 이벤트로 처리 상태를 받을 수 있어야 한다.

```text
namespace: /call-sessions
event: turn_processing_started
event: turn_transcribed
event: turn_response_created
event: turn_completed
event: turn_failed
event: session_force_end
event: session_expired
event: session_ended
event: call_summary_ready
```

예시 payload:

```json
{
  "sessionId": "cm...",
  "turnId": "cm...",
  "clientTurnId": "android-turn-001",
  "status": "TRANSCRIBED",
  "message": "STT completed",
  "timestamp": "2026-05-30T10:00:00.000Z"
}
```

`session_force_end`는 서버가 통화를 강제로 끝내야 할 때 사용한다. 예를 들어 10분 제한 도달, 세션 만료, 내부 정책상 종료가 필요한 상황이 해당된다.

`call_summary_ready`는 통화 종료 후 Android가 방금 한 통화 내용을 보여줄 수 있게 알려주는 이벤트다.

## 9. Backend 구현 계획

### 9.1 CallSessionsService 개선

필요 작업:

- `clientTurnId`를 요청에서 받아 중복 업로드를 방지한다.
- 같은 `clientTurnId`가 재요청되면 기존 결과를 반환한다. 이미 처리 중이면 `409 Conflict`를 반환할 수 있다.
- 세션이 종료/만료된 경우 오디오 업로드를 거절한다.
- 자동 모드 요청인 경우 로그에 `mode=auto_conversation`을 남긴다.
- 기본 5턴 대화 단계와 현재 단계를 관리한다.
- STT 결과에서 종료 의사를 감지하면 마무리 응답 후 `end_call_after_audio`를 반환한다.
- 10분 제한에 도달하면 마무리 응답 후 세션을 종료한다.
- 위험 신호를 감지하면 서버 로그를 우선 남기고, 사용자에게 음성 안내를 제공한다.
- 처리 중 예외가 발생하면 실패 turn을 저장하고 Android가 복구할 수 있는 오류 메시지를 반환한다.

### 9.2 Prisma 모델 검토

ver0.2에서는 DB 마이그레이션을 포함한다. 기존 모델을 확인한 뒤 아래 필드를 추가한다.

`CallSession` 후보 필드:

- `mode`: `manual`, `auto_conversation`
- `status`: `active`, `ended`, `expired`
- `lastTurnAt`
- `startedAt`
- `endedAt`
- `maxDurationMs`
- `targetTurnCount`
- `currentConversationStep`
- `endReason`: `user_requested`, `time_limit`, `expired`, `error`, `manual`

`ConversationTurn` 후보 필드:

- `clientTurnId`
- `status`
- `startedAt`
- `endedAt`
- `durationMs`
- `mode`
- `conversationStep`
- `bargeIn`
- `audioMimeType`
- `failed`
- `failureReason`
- `failureStage`: `stt`, `llm`, `tts`, `unknown`

`clientTurnId`는 세션 안에서 unique 해야 한다.

통화 종료 후 대화 내용 확인 화면을 위해 summary 필드를 별도로 둘지, 기존 turns를 조합해 화면에서 구성할지 결정한다. 초기 구현은 turns 조합으로 충분하지만, 추후 보호자 리포트까지 고려하면 `CallSummary` 모델을 별도 도입할 수 있다.

### 9.3 오류 응답 정책

| 상황 | HTTP | Android 처리 |
| --- | --- | --- |
| 세션 없음 | 404 | 홈으로 복귀 또는 재시작 안내 |
| 세션 종료됨 | 409 | 통화 종료 화면 |
| 같은 발화 중복 | 200 또는 409 | 기존 응답 재사용 또는 무시 |
| STT 빈 결과 | 200 | "다시 말씀해 주세요" 응답 재생 후 LISTENING |
| AI/TTS 실패 | 502 | 재시도 버튼 또는 LISTENING 복귀 |
| 파일 포맷 오류 | 400 | 녹음 포맷 점검 메시지 |
| 10분 제한 도달 | 200 또는 session event | 마무리 멘트 재생 후 종료 |
| 사용자 종료 의사 | 200 | 마무리 멘트 재생 후 종료 |

### 9.4 서버 로그

시연과 디버깅을 위해 아래 로그를 남긴다.

- 세션 생성
- 자동 모드 시작
- 사용자 발화 업로드 시작
- STT 결과
- 대화 단계 전환
- AI 답변 생성
- TTS 생성 완료
- 위험 신호 감지
- 사용자 종료 의사 감지
- turn 완료
- 세션 종료
- 오류 발생

기존 데모 로그 옵션을 재사용하고 `.env`로 켜고 끈다. 개인정보 마스킹은 후속 작업으로 두되, 운영 환경에서는 전체 대화 텍스트 로그를 제한해야 한다.

## 10. Android 구현 계획

### 10.1 자동 청취 루프

Android는 통화중 화면 진입 시 자동으로 마이크 권한을 확인하고 녹음을 준비한다.

권장 루프:

1. `startAutoConversation()`
2. 권한 확인
3. AI 첫 인사 재생
4. `LISTENING` 상태 진입
5. `AudioRecord` 시작
6. RMS 기반 음성 감지
7. 무음 timeout 도달 시 발화 종료
8. 녹음 파일 확정
9. 서버 업로드
10. AI 응답 음성 재생
11. 재생 완료 콜백에서 다시 `LISTENING`

AI 음성 재생 중에도 `AudioRecord`는 사용자 발화를 감지할 수 있어야 한다. 사용자 발화가 감지되면 재생 중인 AI 음성을 중단하고, 끼어든 발화를 새 turn으로 업로드한다.

### 10.2 음성 구간 감지

초기 구현은 정교한 VAD보다 단순 RMS 기반 감지를 권장한다.

기본값:

- 최소 발화 길이: `500ms`
- 발화 종료 무음 기준: `1200ms ~ 1500ms`
- AI 질문 후 무응답 재촉 기준: `3000ms`
- 최대 발화 길이: `12000ms`
- 입력 샘플레이트: 가능하면 `16000Hz`
- 우선 포맷: 기존 `m4a/mp4` 유지, STT 품질 문제가 있으면 변경 가능

실제 품질 기준은 에뮬레이터보다 실제 갤럭시 기기로 잡는다.

### 10.3 AI 재생 중 barge-in 처리

AI 재생 중 사용자가 말을 시작하면 다음 순서로 처리한다.

1. Android가 RMS 상승을 감지한다.
2. 현재 재생 중인 `MediaPlayer` 또는 오디오 플레이어를 즉시 중단한다.
3. UI 상태를 `BARGE_IN_DETECTED`로 전환한다.
4. 사용자 발화 구간을 계속 녹음한다.
5. 발화 종료 후 `bargeIn=true`로 서버에 업로드한다.
6. 서버는 기존 AI 응답이 완전히 재생되지 않았을 수 있음을 고려해 다음 응답을 생성한다.

이 기능 때문에 AI 재생 중 마이크를 완전히 끄면 안 된다.

### 10.4 마이크와 스피커 충돌 대응

AI 재생 중에도 마이크를 유지해야 하므로 echo 문제가 생길 수 있다. 초기 대응은 다음 순서로 한다.

- Android 오디오 모드는 통화/커뮤니케이션에 적합한 설정을 우선 검토한다.
- RMS 기준은 AI 재생음에 의해 오탐되지 않도록 threshold를 조정한다.
- 실제 갤럭시 기기에서 스피커 출력과 마이크 입력을 테스트한다.
- 필요하면 echo cancellation 옵션 또는 Android `AudioRecord` audio source 설정을 조정한다.

### 10.5 UI 변경

통화중 화면 버튼은 다음 3개로 구성한다.

- 스피커
- 통화 종료
- 블루투스

기존 녹음 버튼은 자동 통화 모드에서 제거하거나 블루투스 버튼으로 교체한다.

필수 UI 요소:

- 큰글자 UI 강제 적용
- 대화 기록 표시
- 자동 녹음 중 안내
- 통화 종료 후 방금 한 통화 내용 확인 화면
- 디버깅용 상태 문구: `듣고 있어요`, `답변 준비 중`, `AI가 말하고 있어요`, `다시 듣고 있어요`

실제 서비스에서는 디버깅 상태 문구를 숨길 수 있게 플래그로 관리한다.

### 10.6 통화 종료 후 내용 확인 화면

통화가 끝나면 Android는 바로 홈으로 돌아가지 않고 통화 내용 확인 화면을 보여준다.

표시 후보:

- 통화 시간
- 종료 사유
- 사용자 발화 요약
- AI 주요 답변
- 위험 신호 여부
- 전체 대화 기록

초기 구현은 `GET /call-sessions/:id/turns` 결과로 전체 대화 기록을 보여주는 방식으로 시작한다. 추후 백엔드 summary API를 추가할 수 있다.

## 11. API 계약 변경 요약

`contract/` 업데이트 필요 사항:

- `POST /call-sessions` request에 `mode`, `source`, `callInvitationId` 추가
- `POST /call-sessions/:id/auto-turns/audio` endpoint 추가
- 자동 발화 multipart fields에 `clientTurnId`, `mode`, `startedAt`, `endedAt`, `durationMs`, `bargeIn`, `conversationStep` 추가
- `VoiceTurnResponse`에 `clientTurnId` 추가
- `VoiceTurnResponse`에 `conversationStep`, `nextAction` 추가
- `CallSessionResponse`에 `mode`, `status`, `audioPolicy`, `conversationPolicy` 추가
- 통화 세션 Socket.IO 이벤트 payload 문서화
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
3. `POST /call-sessions/:id/auto-turns/audio` 계약 추가
4. Socket.IO 세션 이벤트 계약 추가
5. Swagger DTO 초안 작성

### Phase 2: Backend

1. `POST /call-sessions`에 자동모드 옵션 추가
2. Prisma migration으로 세션/turn 상태 필드 추가
3. `POST /auto-turns/audio` 구현
4. `clientTurnId` 기반 중복 turn 방지
5. 5턴 대화 단계 관리
6. 종료 의사/10분 제한 처리
7. 위험 신호 서버 로그 처리
8. Socket.IO 처리 상태 이벤트 추가
9. 상태/오류 응답 정리
10. 로그 보강
11. 테스트 추가

### Phase 3: Android

1. 자동 통화 상태머신 추가
2. `AudioRecord` 기반 RMS 음성 감지 구현
3. AI 첫 인사 재생
4. 무응답 3초 재촉 멘트 처리
5. AI 재생 중 barge-in 처리
6. 자동 업로드 및 AI 응답 재생 후 재청취
7. 녹음 버튼을 블루투스 버튼으로 교체
8. 자동 녹음 안내 및 디버깅 상태 문구 추가
9. 통화 종료 후 대화 내용 확인 화면 추가
10. 오류 복구 UI 추가
11. 실제 갤럭시 기기 테스트

### Phase 4: 통합 테스트

1. 서버 실행
2. Android 앱 실행
3. 테스트 전화 요청
4. 수신 화면 확인
5. 전화 받기
6. AI 첫 인사 확인
7. 버튼 조작 없이 5턴 대화 진행
8. AI 재생 중 사용자 발화로 barge-in 동작 확인
9. 통화 종료
10. 통화 내용 확인 화면 확인
11. 서버 로그와 DB turn 기록 확인

## 13. 테스트 시나리오

### 정상 흐름

- 테스트 전화 요청 후 수신 화면이 표시된다.
- 전화 받기를 누르면 통화중 화면으로 이동한다.
- AI가 고정 첫 인사를 먼저 말한다.
- 사용자가 말하면 버튼 없이 자동으로 업로드된다.
- AI 응답 음성이 자동으로 재생된다.
- 재생 후 다시 청취 상태가 된다.
- 기본 5턴 체크 대화가 진행된다.
- 통화 종료가 정상 처리된다.
- 통화 종료 후 대화 내용 확인 화면이 표시된다.

### 무음

- 사용자가 말하지 않으면 업로드하지 않는다.
- AI 질문 후 3초 동안 말이 없으면 `여보세요?` 계열 재촉 멘트를 재생한다.

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

- AI 재생 중 사용자 발화가 감지되면 재생을 중단한다.
- 끼어든 사용자 발화는 `bargeIn=true` turn으로 업로드된다.
- 서버는 끊긴 AI 응답 맥락을 고려해 다음 응답을 생성한다.

### 사용자 종료 의사

- 사용자가 "그만", "끊어", "먼저 끊어"와 같은 종료 의사를 말하면 서버가 종료 의사를 감지한다.
- AI가 마무리 멘트를 생성한다.
- Android는 마무리 멘트 재생 후 통화 종료 화면으로 이동한다.

### 10분 제한

- 세션 시작 후 10분이 지나면 서버가 `session_force_end` 또는 응답 `nextAction=end_call_after_audio`를 반환한다.
- AI는 `어르신 아쉽지만 오늘 통화는 여기까지에요.` 계열 안내를 재생한다.
- Android는 통화 종료 후 내용 확인 화면으로 이동한다.

## 14. 리스크와 대응

| 리스크 | 설명 | 대응 |
| --- | --- | --- |
| 에뮬레이터 마이크 품질 | STT 빈 결과가 자주 발생할 수 있음 | 실제 기기 테스트 병행 |
| 무음 감지 부정확 | 말이 끊기거나 너무 늦게 업로드될 수 있음 | timeout 값을 원격 설정 또는 상수로 관리 |
| AI 응답 지연 | 통화감이 떨어질 수 있음 | 처리중 안내, 짧은 응답 프롬프트 |
| 마이크/스피커 충돌 | AI 음성이 다시 녹음되거나 barge-in 오탐이 생길 수 있음 | `AudioRecord` source, threshold, echo cancellation 옵션 조정 |
| 중복 업로드 | 네트워크 재시도로 같은 turn이 중복 저장될 수 있음 | `clientTurnId` 도입 |
| 배터리/발열 | 계속 녹음 대기 시 부담 | 세션 시간 제한, idle timeout |
| barge-in 구현 복잡도 | AI 재생과 마이크 감지를 동시에 관리해야 함 | 실제 갤럭시 기기 기준으로 먼저 튜닝 |

## 15. 향후 확장

### ver0.3 후보

- WebSocket 기반 audio chunk streaming
- 서버 주도 turn 상태 이벤트
- AI 응답 streaming TTS
- Android TelecomManager 기반 실제 전화 수신 UI
- 보호자에게 위험 감지 알림
- 개인정보 마스킹 로그
- 통화 요약 리포트 API

## 16. MVP 성공 기준

ver0.2 자동 통화 MVP는 아래 조건을 만족하면 성공으로 본다.

- 사용자가 수신 전화를 받고 나면 녹음 버튼을 누르지 않아도 대화가 이어진다.
- 5턴 이상의 사용자-AI 대화가 자동으로 진행된다.
- AI 재생 중 사용자가 말하면 재생이 중단되고 사용자 발화가 처리된다.
- 서버에는 각 turn의 사용자 전사, AI 답변, 실패 여부가 기록된다.
- 세션과 turn 상태가 DB에 명시적으로 저장된다.
- 통화 종료가 정상 처리된다.
- 통화 종료 후 Android에서 방금 한 통화 내용을 볼 수 있다.
- 실제 갤럭시 기기에서 정상 시연 가능하다.
