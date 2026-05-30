import { ApiProperty } from '@nestjs/swagger';

export class AutoTurnResponseDto {
  @ApiProperty({ example: 'cmeyobosay0000session' })
  callSessionId: string;

  @ApiProperty({ example: 'android-turn-20260530-0001' })
  clientTurnId: string;

  @ApiProperty({ example: '자동 통화 음성 업로드를 받았어요.' })
  userText: string;

  @ApiProperty({
    example:
      '좋아요. 자동 통화 응답 흐름이 연결되었어요. 다음 단계에서 실제 음성 인식을 붙일게요.',
  })
  assistantText: string;

  @ApiProperty({ example: null, nullable: true })
  audioMimeType: string | null;

  @ApiProperty({ example: null, nullable: true })
  audioBase64: string | null;

  @ApiProperty({ example: 'play_audio' })
  nextAction: 'play_audio' | 'listen_again' | 'end_call_after_audio';

  @ApiProperty({ example: 'completed' })
  turnStatus: string;

  @ApiProperty({ example: false })
  failed: boolean;

  @ApiProperty({ example: false })
  riskFlag: boolean;

  @ApiProperty({ example: null, nullable: true })
  riskType: string | null;
}
