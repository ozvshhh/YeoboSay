import {
  ConflictException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { randomUUID } from 'node:crypto';
import { CallInvitationResponseDto } from './call-invitation-response.dto';
import { CallInvitationStatus } from './call-invitation-status';
import { CallInvitationsGateway } from './call-invitations.gateway';
import { IncomingCallEventDto } from './incoming-call-event.dto';

const TEST_CALL_INVITATION_TTL_MS = 30 * 1000;
const TEST_CALLER_NAME = 'YeoboSay';
const TEST_CALL_MESSAGE = 'AI 말벗 전화가 왔어요.';

type CallInvitationRecord = {
  id: string;
  status: CallInvitationStatus;
  callerName: string;
  message: string;
  createdAt: Date;
  expiresAt: Date;
  acceptedAt: Date | null;
  declinedAt: Date | null;
};

@Injectable()
export class CallInvitationsService {
  private readonly invitations = new Map<string, CallInvitationRecord>();

  constructor(private readonly gateway: CallInvitationsGateway) {}

  createTestInvitation(): CallInvitationResponseDto {
    const createdAt = new Date();
    const invitation: CallInvitationRecord = {
      id: randomUUID(),
      status: CallInvitationStatus.RINGING,
      callerName: TEST_CALLER_NAME,
      message: TEST_CALL_MESSAGE,
      createdAt,
      expiresAt: new Date(createdAt.getTime() + TEST_CALL_INVITATION_TTL_MS),
      acceptedAt: null,
      declinedAt: null,
    };

    this.invitations.set(invitation.id, invitation);
    this.gateway.emitIncomingCall(this.toIncomingCallEvent(invitation));

    return this.toResponse(invitation);
  }

  accept(id: string): CallInvitationResponseDto {
    const invitation = this.findInvitation(id);
    this.ensureRinging(invitation);

    invitation.status = CallInvitationStatus.ACCEPTED;
    invitation.acceptedAt = new Date();

    return this.toResponse(invitation);
  }

  decline(id: string): CallInvitationResponseDto {
    const invitation = this.findInvitation(id);
    this.ensureRinging(invitation);

    invitation.status = CallInvitationStatus.DECLINED;
    invitation.declinedAt = new Date();

    return this.toResponse(invitation);
  }

  private findInvitation(id: string): CallInvitationRecord {
    const invitation = this.invitations.get(id);

    if (!invitation) {
      throw new NotFoundException('Call invitation not found.');
    }

    this.refreshExpiredStatus(invitation);

    return invitation;
  }

  private ensureRinging(invitation: CallInvitationRecord): void {
    if (invitation.status !== CallInvitationStatus.RINGING) {
      throw new ConflictException('Call invitation is not ringing.');
    }
  }

  private refreshExpiredStatus(invitation: CallInvitationRecord): void {
    if (
      invitation.status === CallInvitationStatus.RINGING &&
      invitation.expiresAt.getTime() <= Date.now()
    ) {
      invitation.status = CallInvitationStatus.EXPIRED;
    }
  }

  private toIncomingCallEvent(
    invitation: CallInvitationRecord,
  ): IncomingCallEventDto {
    return {
      type: 'incoming_call',
      callInvitationId: invitation.id,
      callerName: invitation.callerName,
      message: invitation.message,
      createdAt: invitation.createdAt.toISOString(),
      expiresAt: invitation.expiresAt.toISOString(),
    };
  }

  private toResponse(
    invitation: CallInvitationRecord,
  ): CallInvitationResponseDto {
    return {
      id: invitation.id,
      status: invitation.status,
      callerName: invitation.callerName,
      message: invitation.message,
      createdAt: invitation.createdAt.toISOString(),
      expiresAt: invitation.expiresAt.toISOString(),
      acceptedAt: invitation.acceptedAt?.toISOString() ?? null,
      declinedAt: invitation.declinedAt?.toISOString() ?? null,
    };
  }
}
