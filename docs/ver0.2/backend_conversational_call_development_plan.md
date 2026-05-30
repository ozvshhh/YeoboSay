# YeoboSay ver0.2 Backend Development Plan

Status: Draft  
Date: 2026-05-30  
Related:

- `docs/ver0.2/conversational_call_flow_plan.md`
- `docs/ver0.2/conversational_call_api_spec.md`

## 1. 목표

ver0.2 Backend 개발의 목표는 Android가 자동 감지한 사용자 발화를 안정적으로 처리하고, 전화 통화처럼 이어지는 AI 대화 흐름을 서버에서 관리하는 것이다.

이번 범위에서 Backend는 다음 책임을 가진다.

- 테스트 전화 요청 생성
- 수신 전화 Socket.IO 이벤트 발행
- 통화 초대 accept/decline 상태 관리
- `auto_conversation` 통화 세션 생성
- 세션 상태 명시적 저장
- 발화 turn 상태 명시적 저장
- `clientTurnId` 기반 중복 요청 방지
- STT, AI 응답, TTS 처리
- 5개 기본 대화 주제 흐름 관리
- 무응답/빈 STT 재시도 응답 생성
- 사용자 종료 의도 감지
- 위험 신호 감지 및 서버 로그 기록
- 세션 만료/강제 종료 이벤트 발행
- 통화 종료 후 summary 데이터 제공

## 2. 설계 원칙

- Controller는 얇게 유지하고, 비즈니스 로직은 Service에 둔다.
- Prisma 접근은 Service에서만 수행한다.
- 자동 통화 API는 기존 수동 녹음 API와 분리한다.
- 기존 MVP endpoint는 최대한 유지한다.
- 세션과 turn 상태는 DB 기준으로 재현 가능해야 한다.
- Android 재시도 때문에 같은 요청이 반복되어도 예측 가능하게 동작해야 한다.
- STT 빈 결과는 오류가 아니라 "다시 말씀해 주세요" 응답으로 처리한다.
- STT/LLM/TTS 실패는 구분해서 저장하고 로그에 남긴다.
- Socket.IO는 수신 전화 namespace와 통화 세션 namespace를 분리한다.

## 3. 대상 모듈

예상 변경 범위:

- `backend/src/call-invitations/`
- `backend/src/call-sessions/`
- `backend/src/open-ai/`
- `backend/src/common/`
- `backend/prisma/schema.prisma`
- `backend/prisma/migrations/`
- backend 관련 Swagger DTO
- 필요 시 `contract/`

Android 코드는 Backend 작업 PR에서 수정하지 않는다. API 동작 확인을 위한 문서/contract 수정은 허용한다.

## 4. 데이터 모델 계획

### 4.1 CallInvitation

역할:

- 테스트 전화 요청과 수신 전화 상태를 저장한다.

필드 초안:

| Field | Type | Description |
| --- | --- | --- |
| `id` | string | 서버 생성 ID |
| `status` | enum | `RINGING`, `ACCEPTED`, `DECLINED`, `EXPIRED` |
| `callerName` | string | 예: `세요` |
| `calleeLabel` | string | 예: `왕송길 어르신` |
| `source` | string | `android_test_button`, `backend_test` |
| `createdAt` | DateTime | 생성 시각 |
| `acceptedAt` | DateTime? | 수락 시각 |
| `declinedAt` | DateTime? | 거절 시각 |
| `expiresAt` | DateTime | 만료 시각 |

### 4.2 CallSession

필드 확장:

| Field | Type | Description |
| --- | --- | --- |
| `id` | string | 서버 생성 ID |
| `mode` | enum/string | `manual_recording`, `auto_conversation` |
| `status` | enum/string | `ACTIVE`, `PROCESSING_TURN`, `WAITING_FOR_USER`, `AI_SPEAKING`, `ENDING`, `ENDED`, `EXPIRED` |
| `callInvitationId` | string? | 연결된 초대 ID |
| `currentStep` | string? | 현재 대화 단계 |
| `targetTurnCount` | int | 기본 5 |
| `turnCount` | int | 완료된 사용자 turn 수 |
| `riskFlag` | boolean | 위험 신호 여부 |
| `riskType` | string? | 위험 신호 유형 |
| `startedAt` | DateTime | 시작 시각 |
| `endedAt` | DateTime? | 종료 시각 |
| `expiresAt` | DateTime | 시작 + 10분 |
| `endReason` | string? | 종료 사유 |

### 4.3 ConversationTurn

필드 확장:

| Field | Type | Description |
| --- | --- | --- |
| `id` | string | 서버 생성 ID |
| `sessionId` | string | 세션 ID |
| `clientTurnId` | string? | Android 발화 고유 ID |
| `role` | enum/string | `user`, `assistant`, `system` |
| `status` | enum/string | turn 처리 상태 |
| `conversationStep` | string? | 대화 단계 |
| `userText` | string? | STT 결과 |
| `assistantText` | string? | AI 답변 |
| `audioMimeType` | string? | TTS MIME |
| `audioStorageKey` | string? | 향후 파일 저장용 |
| `bargeIn` | boolean | 끼어들기 여부 |
| `riskFlag` | boolean | 해당 turn 위험 신호 |
| `riskType` | string? | 위험 신호 유형 |
| `errorCode` | string? | 실패 코드 |
| `createdAt` | DateTime | 생성 시각 |
| `completedAt` | DateTime? | 완료 시각 |

제약:

- `(sessionId, clientTurnId)` unique. 단, `clientTurnId`가 null인 assistant/system turn은 제외하거나 별도 처리한다.

### 4.4 CallClientEvent

초기에는 필수는 아니지만, 디버깅과 데모 로그를 위해 저장을 권장한다.

| Field | Type | Description |
| --- | --- | --- |
| `id` | string | 서버 생성 ID |
| `sessionId` | string | 세션 ID |
| `clientEventId` | string? | Android 이벤트 ID |
| `type` | string | 이벤트 타입 |
| `metadata` | Json? | Android 디버그 정보 |
| `createdAt` | DateTime | 서버 수신 시각 |
| `clientTimestamp` | DateTime? | 클라이언트 발생 시각 |

## 5. API 구현 계획

### 5.1 `POST /call-invitations/test`

목적:

- Android 테스트 버튼에서 수신 전화 이벤트를 만들기 위한 endpoint.

동작:

1. `CallInvitation` 생성.
2. 상태 `RINGING` 저장.
3. `/call-invitations` namespace로 `incoming_call` emit.
4. invitation DTO 반환.

주의:

- DB가 꺼져 있으면 명확한 5xx 로그를 남긴다.
- 데모 로그에 invitation ID와 만료 시각을 기록한다.

### 5.2 `POST /call-invitations/:id/accept`

동작:

1. invitation 조회.
2. `RINGING` 상태인지 확인.
3. `ACCEPTED`, `acceptedAt` 저장.
4. DTO 반환.

오류:

- 없음: 404
- 이미 처리됨: 409

### 5.3 `POST /call-invitations/:id/decline`

동작:

1. invitation 조회.
2. `RINGING` 상태인지 확인.
3. `DECLINED`, `declinedAt` 저장.
4. DTO 반환.

### 5.4 `POST /call-sessions`

자동 통화 request:

```json
{
  "mode": "auto_conversation",
  "source": "incoming_call",
  "callInvitationId": "cminv_001"
}
```

동작:

1. mode가 없으면 기존 호환을 위해 `manual_recording`으로 처리.
2. mode가 `auto_conversation`이면 `expiresAt = now + 10분`.
3. 세션 상태 `ACTIVE`로 생성.
4. 첫 AI 인사 assistant turn을 저장.
5. `audioPolicy`, `conversationPolicy`를 응답에 포함.
6. 데모 로그 기록.

첫 인사:

```text
안녕하세요 왕송길 어르신 AI통화 서비스 세요입니다!
```

초기 구현에서는 첫 인사 TTS를 세션 생성 응답에 포함하지 않고 Android가 고정 문구를 재생해도 된다. 다만 일관성을 위해 이후에는 서버가 첫 인사 TTS를 내려주는 구조가 더 좋다.

### 5.5 `POST /call-sessions/:id/auto-turns/audio`

핵심 자동 통화 endpoint.

처리 순서:

1. 세션 조회.
2. 세션 상태가 처리 가능한지 확인.
3. `clientTurnId` 중복 확인.
4. 중복 완료 turn이면 기존 결과 반환.
5. 중복 처리 중 turn이면 409 반환.
6. user turn 생성: `UPLOADED`.
7. 세션 상태를 `PROCESSING_TURN`으로 변경.
8. Socket event `turn_processing_started`.
9. STT 처리: `TRANSCRIBING` -> `TRANSCRIBED`.
10. STT 빈 결과면 retry assistant response 생성.
11. 위험 신호 감지.
12. 종료 의도 감지.
13. 대화 단계 결정.
14. LLM 응답 생성: `RESPONDING` -> `RESPONDED`.
15. TTS 생성: `SYNTHESIZING`.
16. turn `COMPLETED`.
17. 세션 상태 업데이트.
18. Socket event `turn_completed`.
19. HTTP response 반환.

세션 상태 허용:

- `ACTIVE`
- `WAITING_FOR_USER`
- `AI_SPEAKING`

세션 상태 거부:

- `PROCESSING_TURN`, 동일 세션에서 이미 처리 중
- `ENDING`
- `ENDED`
- `EXPIRED`

단, barge-in 요청은 `AI_SPEAKING`에서 허용한다.

### 5.6 `POST /call-sessions/:id/events`

목적:

- Android 상태 이벤트를 서버 로그/DB에 남긴다.

초기 구현:

- 이벤트 저장은 선택.
- 데모 로그 출력은 권장.

예:

- `LISTENING_STARTED`
- `BARGE_IN_DETECTED`
- `NO_RESPONSE_TIMEOUT`
- `CALL_END_BUTTON_PRESSED`

### 5.7 `POST /call-sessions/:id/end`

동작:

1. 세션 조회.
2. 이미 종료된 세션이면 기존 종료 DTO 반환.
3. 진행 중 turn이 있으면 가능한 범위에서 정리.
4. 세션 상태 `ENDED`.
5. `endedAt`, `endReason` 저장.
6. Socket event `session_ended`.
7. 통화 summary 생성 또는 summary 생성 가능 상태로 표시.
8. 데모 로그 기록.

### 5.8 `GET /call-sessions/:id/summary`

Android 종료 후 화면을 위한 endpoint.

초기 구현:

- 저장된 turn 목록 기반으로 반환.
- `summaryText`는 간단한 규칙 기반 문장으로 생성.

추후:

- AI 요약 생성
- 보호자용 요약과 사용자용 요약 분리
- 개인정보 마스킹

## 6. Socket.IO 구현 계획

### 6.1 Namespace 분리

두 namespace를 분리한다.

| Namespace | 책임 |
| --- | --- |
| `/call-invitations` | 수신 전화 초대/취소 |
| `/call-sessions` | 통화 세션 처리 상태/종료/요약 |

### 6.2 `/call-invitations`

Server emit:

- `incoming_call`
- `call_invitation_cancelled`

MVP에서는 인증 없이 전체 연결에 broadcast해도 된다. 실제 운영에서는 대상 사용자 room 분리가 필요하다.

### 6.3 `/call-sessions`

Android 연결 방식:

```text
/call-sessions?sessionId=cmsess_001
```

또는 client event:

```text
join_session
```

Server emit:

- `turn_processing_started`
- `turn_transcribed`
- `turn_response_created`
- `turn_completed`
- `turn_failed`
- `session_force_end`
- `session_expired`
- `session_ended`
- `call_summary_ready`

Socket 이벤트는 HTTP 응답을 대체하지 않는다. Android는 HTTP 응답을 기준으로 주요 흐름을 진행하고, Socket은 상태 표시/강제 종료/디버깅에 사용한다.

## 7. 대화 오케스트레이션

### 7.1 기본 단계

서버는 세션의 `currentStep`과 완료된 사용자 turn 수를 기준으로 다음 질문을 정한다.

권장 순서:

1. `wellbeing`: 오늘 하루 어떠셨어요?
2. `meal`: 식사는 잘 챙겨 드셨어요?
3. `health`: 몸 불편한 곳은 없으세요?
4. `medication`: 약은 잘 챙겨 드셨어요?
5. `sleep`: 잠은 잘 주무셨어요?
6. `schedule`: 오늘이나 내일 일정 있으세요?
7. `mood`: 요즘 마음은 어떠세요?
8. `free_talk`: 더 이야기하고 싶어 하는 경우
9. `closing`: 종료

사용자 요구사항의 기본 성공 기준은 5턴이다. 다만 식사/건강/복약/수면/기분 체크를 자연스럽게 포함하기 위해 서버 prompt는 단계 기반으로 운용한다.

### 7.2 단답 처리

단답이 질문 목적을 충족하면 다음 단계로 넘어간다.

예:

- 질문: 약 드셨어요?
- 답변: 네
- 처리: 목적 충족, 다음 단계

질문 목적을 충족하지 못하면 한 번만 쉽게 다시 묻는다.

예:

- 질문: 몸 불편한 곳은 없으세요?
- 답변: 몰라
- 처리: 한 번 더 쉬운 질문

두 번째도 불명확하면 다음 단계로 넘어간다.

### 7.3 무응답 처리

Android가 3초 무응답을 감지하고 재촉 멘트를 재생한다.

서버는 다음 경우에도 retry 응답을 만든다.

- STT 결과가 빈 문자열
- 오디오가 너무 짧거나 의미 없는 소리

응답:

```json
{
  "assistantText": "죄송해요, 잘 못 들었어요. 다시 한 번 말씀해 주세요.",
  "nextAction": "listen_again"
}
```

### 7.4 종료 의도 처리

아래 표현은 종료 의도로 본다.

- 그만
- 끊어
- 먼저 끊어
- 이제 됐어
- 통화 그만
- 다음에 하자

종료 의도 감지 시:

1. 마무리 멘트를 생성한다.
2. TTS를 생성한다.
3. `nextAction=end_call_after_audio`.
4. 세션을 `ENDING` 또는 `ENDED`로 전환한다.

### 7.5 최대 통화 시간

최대 10분.

만료 시:

1. 세션 상태 `EXPIRED` 또는 `ENDING`.
2. `session_expired` event 발행.
3. 마무리 문구 사용:

```text
어르신 아쉽지만 오늘 통화는 여기까지에요.
```

## 8. OpenAI 처리 계획

### 8.1 STT

입력:

- Android 업로드 오디오 파일
- MIME type

출력:

- `userText`

실패 처리:

- API 실패: `FAILED_STT`
- 빈 결과: retry assistant response

### 8.2 LLM 응답

프롬프트 정책:

- 싹싹하고 예의바른 남자 청년 말벗 톤
- 노인 사용자에게 쉬운 표현 사용
- 한 번에 너무 긴 질문 금지
- 단계별 체크를 자연스럽게 수행
- 위험 신호가 있으면 위험 안내
- 종료 의도가 있으면 마무리

응답 생성에 포함할 컨텍스트:

- 세션 currentStep
- 최근 대화 turn
- 현재 userText
- 기본 5턴 체크 상태
- 위험 신호 여부
- 종료 의도 여부
- barge-in 여부

### 8.3 TTS

출력:

- `audioMimeType`
- `audioBase64`

실패 처리:

- `FAILED_TTS`
- 가능하면 텍스트 응답만 반환하고 `audioBase64=null`로 fallback할지 결정 필요
- Android가 음성 없는 응답을 받을 수 있도록 명세를 보완할 수 있다

## 9. 위험 신호 처리

초기 위험 신호는 서버 로그 우선이다.

감지 후보:

- 아프다
- 숨이 안 쉰다
- 넘어졌다
- 죽고 싶다
- 너무 어지럽다
- 약을 너무 많이 먹었다
- 도와줘

처리:

1. turn에 `riskFlag=true`, `riskType` 저장.
2. session에도 `riskFlag=true`.
3. 데모 로그 출력.
4. AI 음성으로 위험 안내.

보호자 알림은 이번 범위가 아니다.

## 10. Demo Logging

`.env`로 제어한다.

```env
DEMO_LOG_ENABLED=true
DEMO_LOG_TEXT_ENABLED=true
```

로그 대상:

- call invitation created
- call invitation accepted/declined
- call session created
- auto turn uploaded
- STT result
- assistant text
- TTS result
- risk flag
- session ended
- summary ready

텍스트 로그는 데모에는 유용하지만, 운영 전에는 개인정보 마스킹이 필요하다.

## 11. 오류 처리 정책

### 11.1 DB 오류

- Prisma 오류를 그대로 노출하지 않는다.
- 서버 로그에는 상세 원인을 남긴다.
- 클라이언트에는 일반화된 오류를 반환한다.

### 11.2 중복 turn

완료된 `clientTurnId`:

- 기존 결과 반환.

처리 중 `clientTurnId`:

- `409 TURN_ALREADY_PROCESSING`.

### 11.3 세션 상태 오류

종료된 세션에 업로드:

- `409 INVALID_STATE`.

없는 세션:

- `404 NOT_FOUND`.

### 11.4 OpenAI 오류

구분 저장:

- `FAILED_STT`
- `FAILED_LLM`
- `FAILED_TTS`

Socket event:

- `turn_failed`

HTTP:

- 복구 가능하면 fallback 응답
- 복구 불가하면 502

## 12. 테스트 계획

### 12.1 Unit Tests

대상:

- call invitation 상태 전환
- call session 생성
- session mode 처리
- `clientTurnId` 중복 처리
- 종료 의도 감지
- 대화 step 전환
- 빈 STT retry 응답

### 12.2 Integration Tests

대상:

- `POST /call-invitations/test`
- `POST /call-invitations/:id/accept`
- `POST /call-sessions`
- `POST /call-sessions/:id/auto-turns/audio`
- `POST /call-sessions/:id/end`
- `GET /call-sessions/:id/summary`

OpenAI 호출은 mock 처리한다.

### 12.3 Manual Test

1. Docker PostgreSQL 실행.
2. Prisma migrate.
3. Backend start.
4. Android에서 테스트 전화 요청.
5. 서버 로그 확인.
6. 5턴 자동 대화 확인.
7. 종료 후 summary 확인.

## 13. 구현 순서

1. Prisma schema 상태 필드 추가
2. migration 생성
3. DTO/enums 추가
4. call invitation 상태 전환 정리
5. `auto_conversation` 세션 생성 응답 확장
6. 자동 turn 업로드 endpoint 추가
7. `clientTurnId` idempotency 처리
8. 대화 step orchestration service 추가
9. 종료 의도/risk 감지 유틸 추가
10. Socket.IO `/call-sessions` namespace 추가
11. summary endpoint 추가
12. demo logging 보강
13. Swagger decorators 반영
14. 테스트 추가

## 14. 비범위

이번 Backend 작업에서 제외한다.

- 인증/인가
- 보호자 알림
- 운영 모니터링 대시보드
- 실시간 오디오 스트리밍
- WebRTC
- 결제/사용자 계정
- 개인정보 마스킹 완성본

## 15. 완료 기준

Backend 개발은 다음을 만족하면 ver0.2 MVP 기준으로 완료한다.

1. 테스트 전화 요청 생성과 Socket 이벤트 발행이 동작한다.
2. `auto_conversation` 세션 생성이 동작한다.
3. 세션 상태와 turn 상태가 DB에 저장된다.
4. `clientTurnId` 중복 처리가 동작한다.
5. `auto-turns/audio`가 STT, AI 응답, TTS를 처리한다.
6. STT 빈 결과가 retry 응답으로 처리된다.
7. 종료 의도 발화가 `end_call_after_audio`로 처리된다.
8. 10분 만료 정책이 존재한다.
9. 위험 신호가 서버 로그와 DB에 남는다.
10. 통화 summary 조회가 가능하다.
11. Socket.IO 상태 이벤트가 발행된다.
12. 아래 명령이 통과한다.

```bash
cd backend
npm run build
npm run test
npm run lint
npx prisma validate
```
