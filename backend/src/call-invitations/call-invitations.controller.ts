import { Controller, HttpCode, HttpStatus, Param, Post } from '@nestjs/common';
import {
  ApiConflictResponse,
  ApiCreatedResponse,
  ApiNotFoundResponse,
  ApiOkResponse,
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
  @ApiCreatedResponse({ type: CallInvitationResponseDto })
  createTestInvitation(): CallInvitationResponseDto {
    return this.callInvitationsService.createTestInvitation();
  }

  @Post(':id/accept')
  @HttpCode(HttpStatus.OK)
  @ApiOkResponse({ type: CallInvitationResponseDto })
  @ApiNotFoundResponse({ description: 'Call invitation not found.' })
  @ApiConflictResponse({ description: 'Call invitation is not ringing.' })
  accept(@Param('id') id: string): CallInvitationResponseDto {
    return this.callInvitationsService.accept(id);
  }

  @Post(':id/decline')
  @HttpCode(HttpStatus.OK)
  @ApiOkResponse({ type: CallInvitationResponseDto })
  @ApiNotFoundResponse({ description: 'Call invitation not found.' })
  @ApiConflictResponse({ description: 'Call invitation is not ringing.' })
  decline(@Param('id') id: string): CallInvitationResponseDto {
    return this.callInvitationsService.decline(id);
  }
}
