import { ApiProperty } from '@nestjs/swagger';

export class IncomingCallEventDto {
  @ApiProperty({ example: 'incoming_call' })
  type!: 'incoming_call';

  @ApiProperty({ example: 'call-invitation-id' })
  callInvitationId!: string;

  @ApiProperty({ example: 'YeoboSay' })
  callerName!: string;

  @ApiProperty({ example: 'AI 말벗 전화가 왔어요.' })
  message!: string;

  @ApiProperty({ example: '2026-05-28T10:00:00.000Z' })
  createdAt!: string;

  @ApiProperty({ example: '2026-05-28T10:00:30.000Z' })
  expiresAt!: string;
}
