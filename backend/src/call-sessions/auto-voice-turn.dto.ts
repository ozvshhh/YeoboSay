import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import {
  IsBooleanString,
  IsIn,
  IsISO8601,
  IsNumberString,
  IsOptional,
  IsString,
} from 'class-validator';

const AUTO_TURN_MODES = ['auto_conversation'] as const;
const AUTO_TURN_NEXT_ACTIONS = [
  'play_audio',
  'listen_again',
  'end_call_after_audio',
  'force_end',
] as const;

export type AutoTurnNextAction = (typeof AUTO_TURN_NEXT_ACTIONS)[number];

export class AutoVoiceTurnUploadDto {
  @ApiProperty({
    example: 'android-turn-1700000000000',
    description: 'Android-generated id for idempotent automatic turn upload.',
  })
  @IsString()
  clientTurnId!: string;

  @ApiProperty({
    enum: AUTO_TURN_MODES,
    example: 'auto_conversation',
  })
  @IsIn(AUTO_TURN_MODES)
  mode!: 'auto_conversation';

  @ApiPropertyOptional({ example: '2026-05-30T06:00:20.000Z' })
  @IsOptional()
  @IsISO8601()
  startedAt?: string;

  @ApiPropertyOptional({ example: '2026-05-30T06:00:23.000Z' })
  @IsOptional()
  @IsISO8601()
  endedAt?: string;

  @ApiPropertyOptional({ example: '3200' })
  @IsOptional()
  @IsNumberString()
  durationMs?: string;

  @ApiPropertyOptional({ example: 'audio/mp4' })
  @IsOptional()
  @IsString()
  mimeType?: string;

  @ApiPropertyOptional({ example: 'false' })
  @IsOptional()
  @IsBooleanString()
  bargeIn?: string;

  @ApiPropertyOptional({ example: 'health' })
  @IsOptional()
  @IsString()
  conversationStep?: string;
}

export class AutoVoiceTurnResponseDto {
  @ApiProperty({ example: 'cmeyobosay0000turn' })
  turnId!: string;

  @ApiProperty({ example: 'android-turn-1700000000000' })
  clientTurnId!: string;

  @ApiProperty({ example: 'cmeyobosay0000session' })
  sessionId!: string;

  @ApiProperty({ example: '자동 발화가 접수됐어요.' })
  userText!: string;

  @ApiProperty({
    example: '말씀을 잘 받았어요. 다음 단계에서 실제 AI 답변으로 연결할게요.',
  })
  assistantText!: string;

  @ApiProperty({ example: 'audio/mpeg', nullable: true })
  audioMimeType!: string | null;

  @ApiProperty({ nullable: true })
  audioBase64!: string | null;

  @ApiProperty({ example: 'wellbeing', nullable: true })
  conversationStep!: string | null;

  @ApiProperty({ enum: AUTO_TURN_NEXT_ACTIONS, example: 'play_audio' })
  nextAction!: AutoTurnNextAction;

  @ApiProperty({ example: false })
  failed!: boolean;

  @ApiProperty({ example: false })
  riskFlag!: boolean;

  @ApiProperty({ example: 'EMOTIONAL_DISTRESS', nullable: true })
  riskType!: string | null;
}
