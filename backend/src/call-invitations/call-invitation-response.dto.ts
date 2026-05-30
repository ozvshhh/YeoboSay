import { ApiProperty } from '@nestjs/swagger';
import { CallInvitationStatus } from './call-invitation-status';

export class CallInvitationResponseDto {
  @ApiProperty({ example: 'call-invitation-id' })
  id!: string;

  @ApiProperty({ enum: CallInvitationStatus, example: 'RINGING' })
  status!: CallInvitationStatus;

  @ApiProperty({ example: 'YeoboSay' })
  callerName!: string;

  @ApiProperty({ example: 'AI 말벗 전화가 왔어요.' })
  message!: string;

  @ApiProperty({ example: '2026-05-28T10:00:00.000Z' })
  createdAt!: string;

  @ApiProperty({ example: '2026-05-28T10:00:30.000Z' })
  expiresAt!: string;

  @ApiProperty({ example: null, nullable: true })
  acceptedAt!: string | null;

  @ApiProperty({ example: null, nullable: true })
  declinedAt!: string | null;
}
