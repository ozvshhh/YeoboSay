import { Module } from '@nestjs/common';
import { CallInvitationsController } from './call-invitations.controller';
import { CallInvitationsGateway } from './call-invitations.gateway';
import { CallInvitationsService } from './call-invitations.service';

@Module({
  controllers: [CallInvitationsController],
  providers: [CallInvitationsGateway, CallInvitationsService],
})
export class CallInvitationsModule {}
