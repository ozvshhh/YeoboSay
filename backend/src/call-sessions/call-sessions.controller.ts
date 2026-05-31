import {
  Controller,
  Get,
  HttpCode,
  HttpStatus,
  Body,
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
import {
  AutoVoiceTurnResponseDto,
  AutoVoiceTurnUploadDto,
} from './auto-voice-turn.dto';
import { CallSessionResponseDto } from './call-session-response.dto';
import { CallSessionsService } from './call-sessions.service';
import { ConversationTurnListResponseDto } from './conversation-turn-response.dto';
import { CreateCallSessionDto } from './create-call-session.dto';
import { VoiceTurnResponseDto } from './voice-turn-response.dto';

const MAX_AUDIO_UPLOAD_BYTES = 10 * 1024 * 1024;

@ApiTags('Call Sessions')
@Controller('call-sessions')
export class CallSessionsController {
  constructor(private readonly callSessionsService: CallSessionsService) {}

  @Post()
  @ApiBody({ type: CreateCallSessionDto, required: false })
  @ApiCreatedResponse({ type: CallSessionResponseDto })
  create(@Body() dto: CreateCallSessionDto): Promise<CallSessionResponseDto> {
    return this.callSessionsService.create(dto);
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

  @Post(':id/auto-turns/audio')
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
      required: ['audio', 'clientTurnId', 'mode'],
      properties: {
        audio: {
          type: 'string',
          format: 'binary',
          description:
            'Automatic utterance audio file recorded by Android AudioRecord.',
        },
        clientTurnId: {
          type: 'string',
          example: 'android-turn-1700000000000',
        },
        mode: {
          type: 'string',
          enum: ['auto_conversation'],
          example: 'auto_conversation',
        },
        startedAt: { type: 'string', format: 'date-time' },
        endedAt: { type: 'string', format: 'date-time' },
        durationMs: { type: 'string', example: '3200' },
        mimeType: { type: 'string', example: 'audio/mp4' },
        bargeIn: { type: 'string', example: 'false' },
        conversationStep: { type: 'string', example: 'wellbeing' },
      },
    },
  })
  @ApiOkResponse({ type: AutoVoiceTurnResponseDto })
  @ApiBadRequestResponse({ description: 'Invalid or missing audio upload.' })
  @ApiNotFoundResponse({ description: 'Call session not found.' })
  @ApiConflictResponse({
    description:
      'Call session is not processable, or the same clientTurnId is still processing.',
  })
  processAutoAudioTurn(
    @Param('id') id: string,
    @Body() dto: AutoVoiceTurnUploadDto,
    @UploadedFile() audio?: Express.Multer.File,
  ): Promise<AutoVoiceTurnResponseDto> {
    return this.callSessionsService.processAutoAudioTurn(id, dto, audio);
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
