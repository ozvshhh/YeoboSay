import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';

export class AudioPolicyResponseDto {
  @ApiProperty({ example: 3000 })
  silenceTimeoutMs: number;

  @ApiProperty({ example: 30000 })
  maxUtteranceMs: number;

  @ApiProperty({ example: 'audio/mp4' })
  uploadMimeType: string;

  @ApiProperty({ example: true })
  bargeInEnabled: boolean;
}

export class ConversationPolicyResponseDto {
  @ApiProperty({
    example: '안녕하세요 왕송길 어르신 AI통화 서비스 세요입니다!',
  })
  firstGreetingText: string;

  @ApiPropertyOptional({
    example: 'audio/mpeg',
    nullable: true,
    description: 'Server-generated greeting audio MIME type.',
  })
  firstGreetingAudioMimeType?: string | null;

  @ApiPropertyOptional({
    example: 'base64-encoded-mp3',
    nullable: true,
    description: 'Server-generated greeting audio encoded as base64.',
  })
  firstGreetingAudioBase64?: string | null;

  @ApiProperty({ example: '여보세요? 제 말 들리세요?' })
  noResponsePromptText: string;

  @ApiProperty({ example: '어르신 아쉽지만 오늘 통화는 여기까지에요.' })
  maxDurationClosingText: string;

  @ApiProperty({ example: 5 })
  targetTurnCount: number;

  @ApiProperty({ example: 600 })
  maxDurationSeconds: number;
}

export class CallSessionResponseDto {
  @ApiProperty({ example: 'cmeyobosay0000session' })
  id: string;

  @ApiProperty({
    enum: [
      'ACTIVE',
      'PROCESSING_TURN',
      'WAITING_FOR_USER',
      'AI_SPEAKING',
      'ENDING',
      'ENDED',
      'EXPIRED',
    ],
    example: 'ACTIVE',
  })
  status:
    | 'ACTIVE'
    | 'PROCESSING_TURN'
    | 'WAITING_FOR_USER'
    | 'AI_SPEAKING'
    | 'ENDING'
    | 'ENDED'
    | 'EXPIRED';

  @ApiProperty({
    enum: ['manual_recording', 'auto_conversation'],
    example: 'auto_conversation',
  })
  mode: 'manual_recording' | 'auto_conversation';

  @ApiPropertyOptional({
    enum: [
      'greeting',
      'wellbeing',
      'meal',
      'health',
      'medication',
      'sleep',
      'schedule',
      'mood',
      'free_talk',
      'closing',
    ],
    nullable: true,
    example: 'greeting',
  })
  currentStep: string | null;

  @ApiProperty({ example: 0 })
  turnCount: number;

  @ApiProperty({ example: 5 })
  targetTurnCount: number;

  @ApiProperty({ example: false })
  riskFlag: boolean;

  @ApiProperty({ example: 'MEDICAL_EMERGENCY', nullable: true })
  riskType: string | null;

  @ApiProperty({ example: '2026-05-16T05:00:00.000Z' })
  startedAt: string;

  @ApiProperty({
    example: '2026-05-16T05:10:00.000Z',
    nullable: true,
  })
  endedAt: string | null;

  @ApiProperty({ example: '2026-05-16T05:10:00.000Z' })
  expiresAt: string;

  @ApiPropertyOptional({ type: AudioPolicyResponseDto })
  audioPolicy?: AudioPolicyResponseDto;

  @ApiPropertyOptional({ type: ConversationPolicyResponseDto })
  conversationPolicy?: ConversationPolicyResponseDto;
}
