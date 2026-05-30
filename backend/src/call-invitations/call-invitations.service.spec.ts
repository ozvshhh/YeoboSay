import { ConflictException, NotFoundException } from '@nestjs/common';
import { CallInvitationStatus } from './call-invitation-status';
import { CallInvitationsGateway } from './call-invitations.gateway';
import { CallInvitationsService } from './call-invitations.service';

const now = new Date('2026-05-28T05:00:00.000Z');
const expiresAt = new Date('2026-05-28T05:00:30.000Z');

describe('CallInvitationsService', () => {
  let service: CallInvitationsService;
  let gateway: {
    emitIncomingCall: jest.Mock;
  };

  beforeEach(() => {
    jest.useFakeTimers();
    jest.setSystemTime(now);

    gateway = {
      emitIncomingCall: jest.fn(),
    };

    service = new CallInvitationsService(
      gateway as unknown as CallInvitationsGateway,
    );
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it('creates a test call invitation and emits an incoming call event', () => {
    const invitation = service.createTestInvitation();

    expect(typeof invitation.id).toBe('string');
    expect(invitation.id.length).toBeGreaterThan(0);
    expect(invitation).toEqual({
      id: invitation.id,
      status: CallInvitationStatus.RINGING,
      callerName: 'YeoboSay',
      message: 'AI 말벗 전화가 왔어요.',
      createdAt: now.toISOString(),
      expiresAt: expiresAt.toISOString(),
      acceptedAt: null,
      declinedAt: null,
    });
    expect(gateway.emitIncomingCall).toHaveBeenCalledWith({
      type: 'incoming_call',
      callInvitationId: invitation.id,
      callerName: 'YeoboSay',
      message: 'AI 말벗 전화가 왔어요.',
      createdAt: now.toISOString(),
      expiresAt: expiresAt.toISOString(),
    });
  });

  it('accepts a ringing call invitation', () => {
    const invitation = service.createTestInvitation();

    const accepted = service.accept(invitation.id);

    expect(accepted).toEqual({
      ...invitation,
      status: CallInvitationStatus.ACCEPTED,
      acceptedAt: now.toISOString(),
    });
  });

  it('declines a ringing call invitation', () => {
    const invitation = service.createTestInvitation();

    const declined = service.decline(invitation.id);

    expect(declined).toEqual({
      ...invitation,
      status: CallInvitationStatus.DECLINED,
      declinedAt: now.toISOString(),
    });
  });

  it('throws not found for missing invitations', () => {
    expect(() => service.accept('missing')).toThrow(NotFoundException);
  });

  it('throws conflict when an invitation is already handled', () => {
    const invitation = service.createTestInvitation();
    service.accept(invitation.id);

    expect(() => service.decline(invitation.id)).toThrow(ConflictException);
  });

  it('throws conflict when an invitation is expired', () => {
    const invitation = service.createTestInvitation();

    jest.setSystemTime(new Date('2026-05-28T05:00:31.000Z'));

    expect(() => service.accept(invitation.id)).toThrow(ConflictException);
  });
});
