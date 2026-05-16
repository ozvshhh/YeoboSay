# YeoboSay Backend MVP ver0.1 개발기획서

## 1. 목적

Backend MVP ver0.1의 목적은 Android 앱에서 업로드한 사용자 음성을 받아 OpenAI API로 STT, AI 응답 생성, TTS를 처리하고, 통화 세션과 대화 턴을 PostgreSQL에 저장하는 것이다.

이 버전은 HTTP REST 기반으로 구현한다. WebSocket, WebRTC, 실제 전화망 연동은 포함하지 않는다.

OpenAI Realtime API도 ver0.1에서는 직접 구현하지 않는다. 다만 `CallSession`, `ConversationTurn`, OpenAI 호출 서비스, 음성 턴 처리 서비스를 분리해 이후 WebSocket/WebRTC 기반 실시간 통화로 확장할 수 있게 한다.

## 2. Backend MVP 범위

### 포함하는 기능

- OpenAI API 연동
- 통화 세션 생성
- 통화 세션 종료
- 음성 파일 업로드 API
- STT 처리
- AI 응답 생성
- TTS 처리
- 통화 세션 저장
- 대화 턴 저장
- 통화 세션 조회
- 대화 턴 목록 조회
- 위험 발화 감지 결과 저장 및 로그 출력
- Swagger 문서화
- 기본 오류 처리
- 테스트

### 포함하지 않는 기능

- Android UI
- 실제 전화번호 기반 통화
- WebRTC signaling
- WebSocket 실시간 통화
- OpenAI Realtime API
- AI 응답 중간 끊기
- 사용자 인증
- 보호자 알림
- 푸시 알림
- 운영 배포

## 3. 환경변수

필수 환경변수:

```env
DATABASE_URL="postgresql://yeobosay:yeobosay@localhost:5432/yeobosay"
OPENAI_API_KEY="OpenAI API key"
PORT=3000
NODE_ENV=development
```

필요 작업:

- `backend/.env.example`에 `OPENAI_API_KEY` 추가
- `.env`는 커밋하지 않음
- `ConfigService`로 환경변수 접근
- `OPENAI_API_KEY`가 없으면 OpenAI 기능 초기화 시 명확한 오류 발생

## 4. 패키지 의존성

필요 패키지:

```bash
npm install openai multer
npm install -D @types/multer
```

이미 존재해야 하는 기반:

- `@nestjs/config`
- `@nestjs/swagger`
- `@prisma/client`
- `prisma`
- `class-validator`
- `class-transformer`

## 5. 모듈 구조

권장 구조:

```text
backend/src/call-sessions/
  call-sessions.module.ts
  call-sessions.controller.ts
  call-sessions.service.ts
  dto/
    call-session-response.dto.ts
    voice-turn-response.dto.ts

backend/src/openai/
  openai.module.ts
  openai.service.ts

backend/src/prisma/
  prisma.module.ts
  prisma.service.ts
```

역할:

- Controller: HTTP 요청/응답과 Swagger 문서화
- Service: 비즈니스 로직
- OpenAIService: OpenAI API 호출 전담
- PrismaService: DB 접근 전담

## 6. Prisma 모델

MVP 최소 모델:

```prisma
enum CallSessionStatus {
  ACTIVE
  ENDED
}

enum ConversationRole {
  USER
  ASSISTANT
}

model CallSession {
  id        String            @id @default(cuid())
  status    CallSessionStatus @default(ACTIVE)
  startedAt DateTime          @default(now())
  endedAt   DateTime?
  expiresAt DateTime
  turns     ConversationTurn[]
}

model ConversationTurn {
  id            String           @id @default(cuid())
  callSessionId String
  role          ConversationRole
  text          String
  failed        Boolean          @default(false)
  riskFlag      Boolean          @default(false)
  riskType      String?
  createdAt     DateTime         @default(now())

  callSession   CallSession      @relation(fields: [callSessionId], references: [id])

  @@index([callSessionId])
}
```

`riskType` 기본 분류:

```text
SELF_HARM
MEDICAL_EMERGENCY
EMOTIONAL_DISTRESS
HELP_REQUEST
UNKNOWN
```

필요 작업:

- `backend/prisma/schema.prisma` 수정
- migration 생성
- migration 커밋
- Prisma Client generate 확인
- 세션 생성 시 `expiresAt`은 `startedAt + 10분`으로 저장

명령:

```bash
cd backend
npx prisma migrate dev --name add-call-sessions
npx prisma validate
```

## 7. API 설계

### 7.1 통화 세션 생성

```http
POST /call-sessions
```

요청 body:

```json
{}
```

응답:

```json
{
  "id": "call-session-id",
  "status": "ACTIVE",
  "startedAt": "2026-05-16T05:00:00.000Z",
  "endedAt": null,
  "expiresAt": "2026-05-16T05:10:00.000Z"
}
```

처리:

- `CallSession` 생성
- 상태는 `ACTIVE`
- 생성된 세션의 `id`, `status`, `startedAt`, `endedAt`, `expiresAt` 반환

### 7.2 통화 세션 종료

```http
POST /call-sessions/:id/end
```

응답:

```json
{
  "id": "call-session-id",
  "status": "ENDED",
  "startedAt": "2026-05-16T05:00:00.000Z",
  "endedAt": "2026-05-16T05:10:00.000Z"
}
```

처리:

- 세션 ID 확인
- 세션이 없으면 `404`
- 이미 종료된 세션이면 `409`
- 활성 세션이면 `ENDED`로 변경
- `endedAt` 저장

### 7.3 통화 세션 조회

```http
GET /call-sessions/:id
```

응답:

```json
{
  "id": "call-session-id",
  "status": "ACTIVE",
  "startedAt": "2026-05-16T05:00:00.000Z",
  "endedAt": null,
  "expiresAt": "2026-05-16T05:10:00.000Z"
}
```

처리:

- 세션이 없으면 `404`
- 세션 정보를 반환

### 7.4 대화 턴 목록 조회

```http
GET /call-sessions/:id/turns
```

응답:

```json
{
  "callSessionId": "call-session-id",
  "turns": [
    {
      "id": "turn-id",
      "role": "USER",
      "text": "오늘 기분이 조금 외로워",
      "failed": false,
      "riskFlag": true,
      "riskType": "EMOTIONAL_DISTRESS",
      "createdAt": "2026-05-16T05:00:10.000Z"
    }
  ]
}
```

처리:

- 세션이 없으면 `404`
- 대화 턴을 `createdAt` 오름차순으로 반환

### 7.5 음성 턴 처리

```http
POST /call-sessions/:id/turns/audio
Content-Type: multipart/form-data
```

요청:

```text
audio: File
```

응답:

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

처리 순서:

1. 세션 조회
2. 세션이 없으면 `404`
3. 세션 상태가 `ACTIVE`가 아니면 `409`
4. 세션이 만료되었으면 `409`
5. 업로드 파일 확인
6. 파일 크기 제한 확인
7. MIME type 확인
8. OpenAI STT 호출
9. 위험 발화 키워드 감지
10. 사용자 발화 저장
11. 최근 대화 턴 조회
12. OpenAI 응답 생성
13. AI 응답 저장
14. OpenAI TTS 호출
15. 오디오를 base64로 변환
16. JSON 응답 반환

OpenAI 응답 생성 또는 TTS가 실패해도 세션 상태는 `ACTIVE`로 유지한다. Android가 실패를 인지할 수 있도록 `failed: true`, `assistantText`, `audioBase64: null` 형태의 JSON 응답을 반환한다.

## 8. OpenAI 연동

구현 시점에 공식 OpenAI 문서를 다시 확인해 모델명을 확정한다. ver0.1 기본 후보는 다음과 같다.

- STT: `gpt-4o-mini-transcribe`
- 응답 생성: 구현 시점 공식 문서 기준 최신 범용 모델
- TTS: `gpt-4o-mini-tts`
- TTS voice: 여성 톤 voice 우선 선택

통화 시작 후 첫 인사말 기본값:

```text
안녕하세요. 저는 YeoboSay 말벗이에요. 오늘은 어떻게 지내셨어요?
```

첫 인사말은 상수로 분리해 추후 페르소나별 인사말로 확장할 수 있게 한다.

### 8.1 STT

입력:

- Android에서 업로드한 음성 파일

출력:

- 사용자 발화 텍스트

구현:

- OpenAI 음성 텍스트 변환 API 사용
- 업로드된 파일 buffer를 OpenAI 요청에 맞게 전달
- 실패 시 `BadGatewayException` 또는 내부 서비스 예외로 변환

### 8.2 AI 응답 생성

입력:

- 사용자 발화
- 최근 대화 턴
- 시스템 프롬프트

시스템 프롬프트 방향:

```text
당신은 고령 사용자를 위한 따뜻하고 차분한 AI 말벗입니다.
짧고 이해하기 쉬운 문장으로 대화합니다.
사용자의 감정을 먼저 공감하고, 한 번에 하나의 질문만 합니다.
의료, 법률, 금융 문제는 전문가에게 상담하도록 안내합니다.
응급 상황으로 보이는 경우 즉시 주변 사람이나 119에 연락하라고 안내합니다.
```

출력:

- AI 응답 텍스트

응답 정책:

- 한국어 입력에는 한국어로 답변
- 영어 입력에는 영어로 답변
- 1~3문장으로 답변
- 따뜻한 말벗 페르소나 사용
- 추후 사용자 지정 페르소나로 확장 가능하게 시스템 프롬프트를 상수나 설정 객체로 분리

### 8.3 TTS

입력:

- AI 응답 텍스트

출력:

- 음성 응답 binary

MVP 응답 방식:

- Backend에서 binary를 base64로 변환
- JSON 응답의 `audioBase64` 필드에 포함
- `audioMimeType`은 `audio/mpeg` 사용 권장
- `failed: true` 응답에서는 `audioBase64`를 `null`로 반환

## 9. 파일 업로드 정책

MVP 허용 MIME type:

- `audio/mp4`

Android ver0.1은 `.m4a` 파일을 `audio/mp4`로 업로드한다. `audio/mpeg`, `audio/wav`, `audio/webm`은 ver0.1에서 열지 않는다.

권장 제한:

- 최대 파일 크기: 10MB
- 최대 발화 길이: Android에서 30초 제한
- 0.5초 미만 녹음은 Android에서 업로드하지 않음

Backend 처리:

- 파일 없으면 `400`
- MIME type 불일치면 `400`
- 파일 크기 초과면 `400`

## 10. 오류 처리

오류 기준:

- 세션 없음: `404 Not Found`
- 종료된 세션에 업로드: `409 Conflict`
- 이미 종료된 세션 종료 요청: `409 Conflict`
- 만료된 세션에 업로드: `409 Conflict`
- 파일 없음: `400 Bad Request`
- 잘못된 파일 형식: `400 Bad Request`
- 파일 크기 초과: `400 Bad Request`
- OpenAI STT 실패: `502 Bad Gateway`
- OpenAI 응답 생성 실패: 세션 유지, `failed: true` 응답
- OpenAI TTS 실패: 세션 유지, `failed: true` 응답
- 알 수 없는 서버 오류: `500 Internal Server Error`

응답은 내부 구현 세부사항을 과하게 노출하지 않는다.

## 11. Swagger 문서화

문서화 대상:

- `POST /call-sessions`
- `POST /call-sessions/:id/end`
- `GET /call-sessions/:id`
- `GET /call-sessions/:id/turns`
- `POST /call-sessions/:id/turns/audio`

음성 업로드 API는 multipart schema를 명시한다.

필수 Swagger 항목:

- endpoint summary
- response DTO
- error status
- multipart file field

## 12. 테스트 계획

단위 테스트:

- 세션 생성 service 테스트
- 세션 종료 service 테스트
- 세션 조회 service 테스트
- 대화 턴 목록 조회 테스트
- 없는 세션 종료 테스트
- 종료된 세션에 음성 업로드 시 실패 테스트
- 종료된 세션 재종료 시 `409` 테스트
- 만료된 세션 업로드 시 `409` 테스트
- OpenAIService mock 기반 음성 턴 처리 테스트

e2e 테스트:

- `POST /call-sessions`
- `POST /call-sessions/:id/end`
- `GET /call-sessions/:id`
- `GET /call-sessions/:id/turns`
- 음성 턴 API는 OpenAI mock 또는 service mock 기반으로 최소 검증

검증 명령:

```bash
cd backend
npm run build
npm run test
npm run lint
npx prisma validate
```

## 13. Backend 구현 작업 단위

### 작업 1: OpenAI 환경 설정

- `OPENAI_API_KEY` 문서화
- `.env.example` 갱신
- OpenAI package 설치
- OpenAI module/service 생성

### 작업 2: DB 모델 추가

- Prisma enum/model 추가
- migration 생성
- Prisma validate 확인

### 작업 3: 세션 API

- `CallSessionsModule`
- `CallSessionsController`
- `CallSessionsService`
- 세션 생성
- 세션 종료
- 세션 조회
- 대화 턴 목록 조회
- 테스트

### 작업 4: 음성 업로드 API

- multipart 업로드 설정
- 파일 검증
- Swagger 문서화

### 작업 5: OpenAI 파이프라인

- STT
- AI 응답 생성
- TTS
- base64 응답 생성

### 작업 6: 안정화

- 오류 처리 정리
- 테스트 추가
- Android 연동 확인

## 14. Backend 완료 기준

- PostgreSQL Docker 컨테이너와 연결 가능
- Prisma migration 적용 가능
- `POST /call-sessions` 동작
- `GET /call-sessions/:id` 동작
- `GET /call-sessions/:id/turns` 동작
- `POST /call-sessions/:id/end` 동작
- `POST /call-sessions/:id/turns/audio` 동작
- OpenAI STT, 응답 생성, TTS 호출 가능
- 대화 턴 DB 저장 가능
- Swagger에서 API 확인 가능
- 필수 검증 명령 통과

완료 검증:

```bash
cd backend
docker compose up -d
npx prisma migrate dev
npm run build
npm run test
npm run lint
npx prisma validate
npm run start:dev
```
