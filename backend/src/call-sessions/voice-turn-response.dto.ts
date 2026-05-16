import { ApiProperty } from '@nestjs/swagger';

export class VoiceTurnResponseDto {
  @ApiProperty({ example: 'cmeyobosay0000session' })
  callSessionId: string;

  @ApiProperty({ example: '오늘은 산책을 다녀왔어요.' })
  userText: string;

  @ApiProperty({ example: '산책을 다녀오셨군요. 바람은 시원했나요?' })
  assistantText: string;

  @ApiProperty({ example: 'audio/mpeg', nullable: true })
  audioMimeType: string | null;

  @ApiProperty({
    description:
      'Base64 encoded MP3 assistant audio. Null when generation failed.',
    nullable: true,
  })
  audioBase64: string | null;

  @ApiProperty({ example: false })
  failed: boolean;

  @ApiProperty({ example: false })
  riskFlag: boolean;

  @ApiProperty({ example: 'EMOTIONAL_DISTRESS', nullable: true })
  riskType: string | null;
}
