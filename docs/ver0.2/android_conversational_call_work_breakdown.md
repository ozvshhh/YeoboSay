# YeoboSay ver0.2 Android Work Breakdown

Status: Draft  
Date: 2026-05-30  
Related:

- `docs/ver0.2/conversational_call_flow_plan.md`
- `docs/ver0.2/conversational_call_api_spec.md`
- `docs/ver0.2/android_conversational_call_development_plan.md`

## 1. 목적

이 문서는 ver0.2 Android 구현을 테스트 가능한 작은 작업 단위로 나누기 위한 개발 실행 계획이다.

목표는 자동 통화 기능을 한 번에 구현하지 않고, 각 작업 단위가 다음 조건을 만족하게 만드는 것이다.

- Android Studio에서 실행 가능한 단위일 것
- 실제 갤럭시 기기 또는 에뮬레이터에서 수동 확인 가능한 단위일 것
- 백엔드 mock 또는 현재 서버 구현 상태와 독립적으로 일부 검증 가능할 것
- UI, API, 오디오, 상태머신을 단계적으로 결합할 것
- 노인 사용자용 큰글자 UI 품질을 유지할 것

## 2. 전체 구현 전략

권장 순서:

1. 현재 첫 화면과 기존 통화 UI를 깨지 않는 선에서 API 모델과 상태 모델을 먼저 정리한다.
2. 수신 전화 화면과 테스트 전화 요청 흐름을 먼저 안정화한다.
3. `auto_conversation` 세션 생성까지 붙여 서버 연동의 첫 성공 지점을 만든다.
4. 그 다음 자동 청취 상태머신을 UI와 연결한다.
5. `AudioRecord` 기반 발화 감지와 파일 생성은 별도 단계로 검증한다.
6. 자동 turn 업로드와 AI 응답 재생을 붙인다.
7. 마지막에 barge-in, 무응답 재촉, 종료, summary 화면을 완성한다.

초기 PR 추천 범위:

- API DTO/클라이언트 모델 정리
- 통화 상태머신 골격
- 수신 전화 화면과 테스트 전화 요청
- `auto_conversation` 세션 생성

이 범위까지 구현되면 백엔드가 아직 STT/TTS를 완성하지 않아도 Android 화면 흐름을 먼저 검증할 수 있다.

## 3. 작업 단위

## 3.1 API DTO와 네트워크 클라이언트 정리

목표:

- ver0.2 API 명세에 맞는 Android request/response 모델을 준비한다.

주요 변경:

- `CallInvitationDto`
- `CreateCallSessionRequest`
- `CreateCallSessionResponse`
- `AudioPolicy`
- `ConversationPolicy`
- `AutoTurnAudioResponse`
- `CallSummaryResponse`
- `ApiError`

대상 API:

```http
POST /call-invitations/test
POST /call-invitations/:id/accept
POST /call-invitations/:id/decline
POST /call-sessions
POST /call-sessions/:id/auto-turns/audio
POST /call-sessions/:id/end
GET /call-sessions/:id/summary
```

검증:

- Android build 성공
- JSON serialization/deserialization unit test 가능하면 추가
- 서버가 꺼져 있어도 앱이 crash하지 않음

완료 기준:

- API 모델이 명세 문서와 필드명이 일치한다.
- 기존 수동 통화 API 사용부를 깨지 않는다.

## 3.2 통화 상태머신 골격

목표:

- 통화 흐름을 UI에서 직접 조합하지 않고 단일 상태 모델로 관리한다.

상태:

- `IDLE`
- `INCOMING`
- `STARTING`
- `AI_GREETING`
- `LISTENING`
- `NO_RESPONSE_PROMPTING`
- `USER_SPEAKING`
- `UPLOADING`
- `AI_PROCESSING`
- `AI_PLAYING`
- `BARGE_IN_DETECTED`
- `ENDING`
- `ENDED`
- `CALL_SUMMARY`
- `ERROR`

주요 변경:

- `CallUiState`
- `CallScreenState`
- `ConversationMessage`
- 상태 전환 함수
- 디버그 상태 텍스트

검증:

- 상태 전환 unit test
- Android Studio Preview 또는 에뮬레이터에서 화면 상태 전환 수동 확인

완료 기준:

- UI가 `CallUiState`만 보고 렌더링된다.
- API/오디오 세부 구현 없이도 상태 전환을 테스트할 수 있다.

## 3.3 첫 화면 테스트 전화 요청

목표:

- 기존 첫 화면을 유지하면서 테스트 전화 요청 버튼을 안정화한다.

주요 변경:

- `테스트 전화 요청` 버튼
- `POST /call-invitations/test` 호출
- 호출 성공/실패 메시지
- 서버 미실행 시 사용자에게 명확한 오류 표시

검증:

- 백엔드 실행 상태에서 버튼 클릭 시 invitation 생성
- 백엔드 미실행 상태에서 crash 없이 오류 표시
- 기존 통화 시작 버튼이 있으면 기존 기능 유지

완료 기준:

- 개발자가 첫 화면에서 수신 전화 테스트를 시작할 수 있다.

## 3.4 Socket.IO `/call-invitations` 연결

목표:

- 백엔드의 수신 전화 이벤트를 받아 수신 화면으로 전환한다.

Namespace:

```text
/call-invitations
```

이벤트:

- `incoming_call`
- `call_invitation_cancelled`

주요 변경:

- Socket.IO client 의존성 확인
- namespace 연결
- 연결 상태 로그
- `incoming_call` 수신 시 `INCOMING`
- 이벤트 수신 실패 시 HTTP 응답 fallback 허용

검증:

- 서버에서 `incoming_call` emit 시 수신 화면 표시
- 연결 실패 시 앱 crash 없음
- 앱 재시작 후 재연결 가능

완료 기준:

- 수신 전화 화면 진입이 HTTP 응답 fallback이 아니라 Socket 이벤트로 동작한다.

## 3.5 수신 전화 화면 구현

목표:

- 삼성 전화 앱 느낌의 큰글자 화이트 테마 수신 화면을 구현한다.

UI 요소:

- 발신자: `세요`
- 설명: `AI 안부 전화`
- 큰 원형 프로필
- 왼쪽 전화 받기 버튼
- 오른쪽 전화 거절 버튼

주요 변경:

- `IncomingCallScreen`
- `AcceptCallButton`
- `DeclineCallButton`
- 큰 터치 영역
- 큰글자 스타일 적용

검증:

- 에뮬레이터 화면 확인
- 실제 갤럭시 화면 확인
- 버튼이 엄지로 누르기 쉬운 크기인지 확인

완료 기준:

- 수신 화면이 현재 앱 UI 품질과 맞고, 노인 사용자가 이해하기 쉽다.

## 3.6 전화 받기/거절 API 연동

목표:

- 수신 화면 버튼을 실제 invitation 상태 전환 API에 연결한다.

대상 API:

```http
POST /call-invitations/:id/accept
POST /call-invitations/:id/decline
```

전화 받기 흐름:

1. `accept` 호출
2. 성공 시 `STARTING`
3. `POST /call-sessions` 호출 준비

전화 거절 흐름:

1. `decline` 호출
2. 성공 또는 실패와 관계없이 사용자가 확인 가능한 상태로 첫 화면 복귀

검증:

- accept 정상
- decline 정상
- 만료된 invitation 처리
- 서버 오류 처리

완료 기준:

- 수신 화면에서 받기/거절이 모두 API와 연결된다.

## 3.7 `auto_conversation` 세션 생성

목표:

- 전화 받기 후 자동 통화 세션을 생성한다.

대상 API:

```http
POST /call-sessions
```

Request:

```json
{
  "mode": "auto_conversation",
  "source": "incoming_call",
  "callInvitationId": "cminv_001"
}
```

주요 변경:

- 세션 ID 저장
- `audioPolicy` 저장
- `conversationPolicy` 저장
- 통화 화면 이동
- `/call-sessions` Socket 연결 준비

검증:

- 서버 응답의 sessionId가 화면 상태에 반영됨
- 세션 생성 실패 시 수신 화면 또는 첫 화면으로 안전하게 복구

완료 기준:

- 전화 받기 후 통화 중 화면까지 진입한다.

## 3.8 Socket.IO `/call-sessions` 연결

목표:

- 통화 처리 상태와 서버 강제 종료 이벤트를 받을 수 있게 한다.

Namespace:

```text
/call-sessions?sessionId={sessionId}
```

이벤트:

- `turn_processing_started`
- `turn_transcribed`
- `turn_response_created`
- `turn_completed`
- `turn_failed`
- `session_force_end`
- `session_expired`
- `session_ended`
- `call_summary_ready`

주요 변경:

- 세션 생성 후 namespace 연결
- 연결 상태를 debug state에 표시
- 서버 종료 이벤트 처리

검증:

- 연결 성공/실패 로그 확인
- 수동 event emit 시 UI debug state 갱신
- `session_expired` 수신 시 종료 흐름 진입

완료 기준:

- Android가 세션 단위 서버 이벤트를 받을 수 있다.

## 3.9 통화 중 화면 상태 연결

목표:

- 통화 중 화면을 상태머신과 연결한다.

UI 요소:

- 통화 시간
- 프로필 원형 아이콘
- 이름: `여보세요`
- 설명: `AI 안부 전화`
- 자동 녹음/청취 안내
- 대화 기록
- 하단 버튼:
  - 스피커
  - 통화 종료
  - 블루투스

주요 변경:

- `ActiveCallScreen`
- elapsed time timer
- `ConversationHistory`
- `CallControlBar`
- debug status optional 표시

검증:

- 통화 화면 진입
- 타이머 증가
- 대화 기록 추가
- 종료 버튼 UI 확인

완료 기준:

- 오디오 기능 없이도 통화 화면의 상태 표시가 정상 동작한다.

## 3.10 AI 첫 인사 재생

목표:

- 세션 생성 후 AI가 먼저 인사한다.

문구:

```text
안녕하세요 왕송길 어르신 AI통화 서비스 세요입니다!
```

초기 구현 선택지:

1. Android TTS로 고정 문구 재생
2. 서버가 첫 인사 TTS를 내려주면 해당 오디오 재생

초기에는 Android TTS fallback을 허용한다.

주요 변경:

- `AI_GREETING` 상태
- 첫 인사 재생
- 재생 완료 후 `LISTENING`

검증:

- 세션 생성 직후 첫 인사 재생
- 재생 완료 후 자동 청취 상태로 전환
- 재생 중 종료 버튼 처리

완료 기준:

- 사용자가 말을 하지 않아도 AI가 먼저 통화를 시작한다.

## 3.11 오디오 권한과 AudioRecord 준비

목표:

- 자동 청취를 위한 마이크 권한과 `AudioRecord` 초기화를 준비한다.

주요 변경:

- `RECORD_AUDIO` 권한 요청
- 권한 거부 UI
- `AudioRecord` 생성/해제
- sample rate 설정
- audio buffer loop 준비

검증:

- 권한 허용 시 녹음 loop 시작 가능
- 권한 거부 시 통화 시작 방지
- 앱 종료/화면 전환 시 resource leak 없음

완료 기준:

- 실제 갤럭시 기기에서 마이크 입력을 읽을 수 있다.

## 3.12 RMS 기반 발화 감지

목표:

- 사용자가 말하기 시작하고 끝나는 시점을 Android에서 감지한다.

기본 정책:

| 항목 | 값 |
| --- | --- |
| 최소 발화 길이 | 500 ms |
| 발화 종료 무음 | 1300 ms |
| 무응답 재촉 | 3000 ms |
| 단일 발화 최대 길이 | 12000 ms |

주요 변경:

- RMS 계산
- noise floor 측정
- speech start/end 감지
- `LISTENING -> USER_SPEAKING -> UPLOADING` 상태 전환

검증:

- 조용한 상태에서 발화 감지 안 됨
- 말하면 `USER_SPEAKING`
- 말이 끝나면 발화 확정
- 실제 갤럭시 기기에서 threshold 튜닝

완료 기준:

- 사용자가 버튼을 누르지 않아도 발화 구간이 안정적으로 잡힌다.

## 3.13 발화 파일 생성

목표:

- 감지된 사용자 발화를 서버에 업로드 가능한 파일로 만든다.

주요 변경:

- PCM buffer 수집
- m4a/mp4 또는 서버가 권장하는 포맷으로 인코딩
- 임시 파일 저장
- 파일 크기/길이 metadata 생성
- `clientTurnId` 생성

검증:

- 생성된 파일이 재생 가능
- 파일 길이가 실제 발화 길이와 대략 일치
- 너무 짧은 발화는 무시

완료 기준:

- 자동 감지된 발화가 upload 가능한 파일로 생성된다.

## 3.14 자동 Turn 업로드

목표:

- 발화 파일을 서버의 자동 turn endpoint로 업로드한다.

대상 API:

```http
POST /call-sessions/:id/auto-turns/audio
Content-Type: multipart/form-data
```

필수 form field:

- `audio`
- `clientTurnId`
- `mode=auto_conversation`

권장 form field:

- `startedAt`
- `endedAt`
- `durationMs`
- `mimeType`
- `bargeIn`
- `conversationStep`
- `clientSequence`

검증:

- 서버 mock 응답 수신
- 네트워크 실패 시 같은 `clientTurnId`로 1회 재시도
- 업로드 중 UI 상태 표시

완료 기준:

- 녹음 버튼 없이 사용자 발화가 서버에 업로드된다.

## 3.15 AI 응답 오디오 재생

목표:

- 서버 응답의 `audioBase64`를 재생한다.

주요 변경:

- base64 decode
- 임시 오디오 파일 저장 또는 stream 재생
- audio focus 처리
- 큰 볼륨 설정
- 재생 완료 callback

`nextAction` 처리:

| nextAction | 동작 |
| --- | --- |
| `play_audio` | 재생 후 `LISTENING` |
| `listen_again` | 재생 후 같은 단계로 `LISTENING` |
| `end_call_after_audio` | 재생 후 종료 |
| `force_end` | 즉시 종료 |

검증:

- AI 오디오 재생
- 재생 완료 후 자동 청취 재개
- `listen_again` 처리
- `end_call_after_audio` 처리

완료 기준:

- 서버 응답이 사용자에게 음성으로 들린다.

## 3.16 Barge-in 처리

목표:

- AI가 말하는 중에도 사용자가 끼어들 수 있게 한다.

주요 변경:

- AI 재생 중에도 RMS monitor 유지
- speech start 감지 시 player stop
- `BARGE_IN_DETECTED`
- 새 사용자 발화 녹음
- 업로드 시 `bargeIn=true`

검증:

- AI 재생 중 말하면 재생 중단
- 중단 후 사용자 발화가 새 turn으로 업로드
- 일반 AI 재생 완료 흐름과 충돌 없음

완료 기준:

- 사용자가 AI 말을 끊고 대화를 이어갈 수 있다.

## 3.17 무응답 3초 재촉

목표:

- AI 질문 후 사용자가 3초 동안 말하지 않으면 재촉 멘트를 재생한다.

재촉 문구:

```text
여보세요? 제 말 들리세요?
```

주요 변경:

- `LISTENING` 진입 시 no-response timer 시작
- speech start 시 timer 취소
- 3초 경과 시 `NO_RESPONSE_PROMPTING`
- 재촉 재생 후 다시 `LISTENING`
- 무한 반복 방지 정책 추가

검증:

- AI 재생 후 아무 말 없으면 3초 뒤 재촉
- 사용자가 말하면 재촉 timer 취소
- 재촉 중 사용자가 말하면 barge-in과 유사하게 처리

완료 기준:

- 사용자가 침묵해도 통화가 멈춰 보이지 않는다.

## 3.18 통화 종료 버튼과 종료 정리

목표:

- 사용자가 통화 종료 버튼을 누르면 모든 리소스를 정리한다.

대상 API:

```http
POST /call-sessions/:id/end
```

주요 변경:

- 종료 버튼 클릭 처리
- `ENDING`
- 녹음 stop
- playback stop
- socket disconnect 또는 room leave
- API end 호출
- `CALL_SUMMARY` 이동

검증:

- 통화 중 종료
- AI 재생 중 종료
- 업로드 중 종료
- 이미 종료된 세션 처리

완료 기준:

- 어떤 상태에서도 종료 버튼이 앱을 안정적으로 첫 화면 또는 summary로 보낸다.

## 3.19 서버 종료 이벤트 처리

목표:

- 서버가 세션 만료 또는 강제 종료를 보냈을 때 Android가 종료 흐름을 따른다.

대상 이벤트:

- `session_force_end`
- `session_expired`
- `session_ended`

주요 변경:

- 서버 종료 이벤트 수신
- 마무리 메시지 표시 또는 재생
- 오디오 리소스 정리
- summary 화면 이동

검증:

- 수동 socket event로 강제 종료
- 10분 만료 시나리오
- 이벤트 중복 수신 처리

완료 기준:

- 서버가 통화를 끝낼 수 있다.

## 3.20 통화 Summary 화면

목표:

- 통화 종료 후 방금 한 대화 내용을 확인할 수 있게 한다.

대상 API:

```http
GET /call-sessions/:id/summary
```

Fallback:

```http
GET /call-sessions/:id/turns
```

주요 변경:

- `CallSummaryScreen`
- 통화 시간
- 대화 turn 수
- 위험 신호 여부
- 대화 기록
- 홈으로 돌아가기

검증:

- summary API 성공
- summary API 실패 시 로컬 대화 기록 표시
- 긴 대화 기록 scroll
- 큰글자 UI 유지

완료 기준:

- 통화 종료 후 사용자가 방금 대화한 내용을 볼 수 있다.

## 3.21 실제 갤럭시 기기 튜닝

목표:

- 에뮬레이터가 아닌 실제 대상 기기 기준으로 오디오 품질을 조정한다.

확인 항목:

- 마이크 입력 감도
- RMS threshold
- 주변 소음 환경
- AI 음량
- 스피커 출력
- 블루투스 버튼 UI 동작
- 장시간 5턴 이상 대화
- 배터리/발열 특이사항

검증:

- 조용한 방
- 일반 생활 소음
- 사용자가 작은 목소리로 말하는 상황
- AI 재생 중 끼어들기

완료 기준:

- 실제 갤럭시 기기에서 5턴 대화 성공률이 수동 테스트 기준으로 충분하다.

## 4. 추천 PR 분리

### PR 1: API 모델과 상태머신 기반

포함:

- 3.1 API DTO와 네트워크 클라이언트 정리
- 3.2 통화 상태머신 골격

이유:

- 이후 UI와 오디오 구현의 기반이다.

### PR 2: 수신 전화 흐름

포함:

- 3.3 첫 화면 테스트 전화 요청
- 3.4 Socket.IO `/call-invitations` 연결
- 3.5 수신 전화 화면 구현
- 3.6 전화 받기/거절 API 연동

이유:

- 백엔드 invitation 구현과 병렬 검증이 가능하다.

### PR 3: 자동 세션과 통화 화면

포함:

- 3.7 `auto_conversation` 세션 생성
- 3.8 Socket.IO `/call-sessions` 연결
- 3.9 통화 중 화면 상태 연결
- 3.10 AI 첫 인사 재생

이유:

- 실제 통화 화면까지 진입하는 첫 end-to-end UI 흐름이다.

### PR 4: 자동 청취와 발화 파일 생성

포함:

- 3.11 오디오 권한과 AudioRecord 준비
- 3.12 RMS 기반 발화 감지
- 3.13 발화 파일 생성

이유:

- 가장 리스크가 큰 오디오 부분을 API 업로드와 분리해 검증한다.

### PR 5: 자동 업로드와 AI 재생

포함:

- 3.14 자동 turn 업로드
- 3.15 AI 응답 오디오 재생

이유:

- 백엔드 `auto-turns/audio` endpoint와 붙는 핵심 구간이다.

### PR 6: 전화다운 상호작용 완성

포함:

- 3.16 Barge-in 처리
- 3.17 무응답 3초 재촉
- 3.18 통화 종료 버튼과 종료 정리
- 3.19 서버 종료 이벤트 처리

이유:

- 사용자가 실제 전화처럼 느끼는 핵심 상호작용을 완성한다.

### PR 7: Summary와 실제 기기 튜닝

포함:

- 3.20 통화 Summary 화면
- 3.21 실제 갤럭시 기기 튜닝

이유:

- 시연/사용자 검증을 위한 마무리 작업이다.

## 5. 공통 검증 명령

Android 작업 단위 완료 전 가능한 범위에서 실행한다.

```bash
cd android
./gradlew build
./gradlew test
```

프로젝트에 lint task가 안정화되어 있으면 추가한다.

```bash
cd android
./gradlew lint
```

수동 확인:

- Android Studio에서 app 실행
- Pixel emulator 실행 확인
- 실제 갤럭시 기기 실행 확인
- 서버 미실행 상태 오류 표시 확인
- 서버 실행 상태 API 연동 확인

## 6. 공통 체크리스트

각 작업 단위 완료 전 확인한다.

- Android 범위 밖 파일을 수정하지 않았다.
- backend 코드를 수정하지 않았다.
- `.idea`, local device config, generated file을 커밋하지 않았다.
- 기존 첫 화면이 깨지지 않았다.
- 큰글자 UI 원칙을 유지했다.
- 통화 종료 버튼이 모든 상태에서 접근 가능하다.
- 서버 오류가 앱 crash로 이어지지 않는다.
- 실제 갤럭시 기기에서 오디오 관련 작업을 최소 1회 확인했다.

## 7. 성공 기준

ver0.2 Android 개발은 다음을 만족하면 완료로 본다.

1. 첫 화면에서 테스트 전화 요청을 보낼 수 있다.
2. Socket 이벤트로 수신 전화 화면이 열린다.
3. 전화 받기 후 `auto_conversation` 세션이 생성된다.
4. AI 첫 인사가 자동 재생된다.
5. 사용자가 버튼 없이 말하면 발화가 감지된다.
6. 발화 오디오가 서버에 자동 업로드된다.
7. 서버 AI 응답 음성이 자동 재생된다.
8. AI 재생 중 사용자가 말하면 재생이 중단된다.
9. 3초 무응답 재촉이 동작한다.
10. 통화 종료 버튼과 서버 종료 이벤트가 모두 동작한다.
11. 통화 종료 후 summary 화면이 표시된다.
12. 실제 갤럭시 기기에서 5턴 대화가 가능하다.
