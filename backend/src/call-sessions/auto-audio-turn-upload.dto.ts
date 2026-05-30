import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import {
  IsBooleanString,
  IsNotEmpty,
  IsOptional,
  IsString,
} from 'class-validator';

export class AutoAudioTurnUploadDto {
  @ApiProperty({
    description:
      'Client-generated turn id used by Android to identify retries for the same utterance.',
    example: 'android-turn-20260530-0001',
  })
  @IsString()
  @IsNotEmpty()
  clientTurnId!: string;

  @ApiPropertyOptional({
    description: 'Whether this utterance interrupted assistant playback.',
    example: 'false',
  })
  @IsOptional()
  @IsBooleanString()
  bargeIn?: string;
}
