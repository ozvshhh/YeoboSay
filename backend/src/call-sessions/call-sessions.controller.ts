import {
  Controller,
  Get,
  HttpCode,
  HttpStatus,
  Param,
  Post,
  UploadedFile,
  UseInterceptors,
} from '@nestjs/common';
import { FileInterceptor } from '@nestjs/platform-express';
import {
  ApiBadRequestResponse,
  ApiBody,
  ApiConflictResponse,
  ApiConsumes,
  ApiCreatedResponse,
  ApiNotFoundResponse,
  ApiOkResponse,
  ApiTags,
} from '@nestjs/swagger';
import { CallSessionResponseDto } from './call-session-response.dto';
import { CallSessionsService } from './call-sessions.service';
import { ConversationTurnListResponseDto } from './conversation-turn-response.dto';
import { VoiceTurnResponseDto } from './voice-turn-response.dto';

const MAX_AUDIO_UPLOAD_BYTES = 10 * 1024 * 1024;

@ApiTags('Call Sessions')
@Controller('call-sessions')
export class CallSessionsController {
  constructor(private readonly callSessionsService: CallSessionsService) {}

  @Post()
  @ApiCreatedResponse({ type: CallSessionResponseDto })
  create(): Promise<CallSessionResponseDto> {
    return this.callSessionsService.create();
  }

  @Get(':id')
  @ApiOkResponse({ type: CallSessionResponseDto })
  @ApiNotFoundResponse({ description: 'Call session not found.' })
  findOne(@Param('id') id: string): Promise<CallSessionResponseDto> {
    return this.callSessionsService.findOne(id);
  }

  @Get(':id/turns')
  @ApiOkResponse({ type: ConversationTurnListResponseDto })
  @ApiNotFoundResponse({ description: 'Call session not found.' })
  findTurns(@Param('id') id: string): Promise<ConversationTurnListResponseDto> {
    return this.callSessionsService.findTurns(id);
  }

  @Post(':id/turns/audio')
  @HttpCode(HttpStatus.OK)
  @UseInterceptors(
    FileInterceptor('audio', {
      limits: { fileSize: MAX_AUDIO_UPLOAD_BYTES },
    }),
  )
  @ApiConsumes('multipart/form-data')
  @ApiBody({
    schema: {
      type: 'object',
      required: ['audio'],
      properties: {
        audio: {
          type: 'string',
          format: 'binary',
          description: 'M4A audio file recorded by Android MediaRecorder.',
        },
      },
    },
  })
  @ApiOkResponse({ type: VoiceTurnResponseDto })
  @ApiBadRequestResponse({ description: 'Invalid or missing audio upload.' })
  @ApiNotFoundResponse({ description: 'Call session not found.' })
  @ApiConflictResponse({ description: 'Call session is ended or expired.' })
  processAudioTurn(
    @Param('id') id: string,
    @UploadedFile() audio?: Express.Multer.File,
  ): Promise<VoiceTurnResponseDto> {
    return this.callSessionsService.processAudioTurn(id, audio);
  }

  @Post(':id/end')
  @HttpCode(HttpStatus.OK)
  @ApiOkResponse({ type: CallSessionResponseDto })
  @ApiNotFoundResponse({ description: 'Call session not found.' })
  @ApiConflictResponse({ description: 'Call session is already ended.' })
  end(@Param('id') id: string): Promise<CallSessionResponseDto> {
    return this.callSessionsService.end(id);
  }
}
