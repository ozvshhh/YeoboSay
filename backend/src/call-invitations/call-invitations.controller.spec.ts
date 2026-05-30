import { CallInvitationsController } from './call-invitations.controller';
import { CallInvitationsService } from './call-invitations.service';

describe('CallInvitationsController', () => {
  let controller: CallInvitationsController;
  let service: {
    createTestInvitation: jest.Mock;
    accept: jest.Mock;
    decline: jest.Mock;
  };

  beforeEach(() => {
    service = {
      createTestInvitation: jest.fn(),
      accept: jest.fn(),
      decline: jest.fn(),
    };
    controller = new CallInvitationsController(
      service as unknown as CallInvitationsService,
    );
  });

  it('creates a test invitation', () => {
    const response = { id: 'invitation-1' };
    service.createTestInvitation.mockReturnValue(response);

    expect(controller.createTestInvitation()).toBe(response);
  });

  it('accepts an invitation', () => {
    const response = { id: 'invitation-1' };
    service.accept.mockReturnValue(response);

    expect(controller.accept('invitation-1')).toBe(response);
    expect(service.accept).toHaveBeenCalledWith('invitation-1');
  });

  it('declines an invitation', () => {
    const response = { id: 'invitation-1' };
    service.decline.mockReturnValue(response);

    expect(controller.decline('invitation-1')).toBe(response);
    expect(service.decline).toHaveBeenCalledWith('invitation-1');
  });
});
