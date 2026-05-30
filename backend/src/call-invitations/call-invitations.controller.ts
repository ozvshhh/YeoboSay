import { Controller, HttpCode, HttpStatus, Param, Post } from '@nestjs/common';
import {
  ApiConflictResponse,
  ApiCreatedResponse,
  ApiNotFoundResponse,
  ApiOkResponse,
  ApiOperation,
  ApiTags,
} from '@nestjs/swagger';
import { CallInvitationResponseDto } from './call-invitation-response.dto';
import { CallInvitationsService } from './call-invitations.service';

@ApiTags('Call Invitations')
@Controller('call-invitations')
export class CallInvitationsController {
  constructor(
    private readonly callInvitationsService: CallInvitationsService,
  ) {}

  @Post('test')
  @ApiOperation({
    summary: 'Create a test incoming call invitation',
    description:
      'Creates a short-lived test call invitation and emits an incoming_call Socket.IO event on the /call-invitations namespace.',
  })
  @ApiCreatedResponse({ type: CallInvitationResponseDto })
  createTestInvitation(): CallInvitationResponseDto {
    return this.callInvitationsService.createTestInvitation();
  }

  @Post(':id/accept')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({
    summary: 'Accept an incoming call invitation',
    description:
      'Marks a ringing invitation as accepted. The Android client should create a call session after this succeeds.',
  })
  @ApiOkResponse({ type: CallInvitationResponseDto })
  @ApiNotFoundResponse({ description: 'Call invitation not found.' })
  @ApiConflictResponse({ description: 'Call invitation is not ringing.' })
  accept(@Param('id') id: string): CallInvitationResponseDto {
    return this.callInvitationsService.accept(id);
  }

  @Post(':id/decline')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({
    summary: 'Decline an incoming call invitation',
    description: 'Marks a ringing invitation as declined.',
  })
  @ApiOkResponse({ type: CallInvitationResponseDto })
  @ApiNotFoundResponse({ description: 'Call invitation not found.' })
  @ApiConflictResponse({ description: 'Call invitation is not ringing.' })
  decline(@Param('id') id: string): CallInvitationResponseDto {
    return this.callInvitationsService.decline(id);
  }
}
