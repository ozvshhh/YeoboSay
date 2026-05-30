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
import { AutoAudioTurnUploadDto } from './auto-audio-turn-upload.dto';
import { AutoTurnResponseDto } from './auto-turn-response.dto';
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
      required: ['clientTurnId', 'audio'],
      properties: {
        clientTurnId: {
          type: 'string',
          description: 'Client-generated id for this automatic utterance.',
          example: 'android-turn-20260530-0001',
        },
        bargeIn: {
          type: 'string',
          description: 'Boolean string indicating barge-in during AI playback.',
          example: 'false',
        },
        audio: {
          type: 'string',
          format: 'binary',
          description: 'M4A audio file captured by Android automatic mode.',
        },
      },
    },
  })
  @ApiOkResponse({ type: AutoTurnResponseDto })
  @ApiBadRequestResponse({ description: 'Invalid or missing audio upload.' })
  @ApiNotFoundResponse({ description: 'Call session not found.' })
  @ApiConflictResponse({
    description: 'Call session is not auto mode, ended, or expired.',
  })
  processAutoAudioTurn(
    @Param('id') id: string,
    @Body() dto: AutoAudioTurnUploadDto,
    @UploadedFile() audio?: Express.Multer.File,
  ): Promise<AutoTurnResponseDto> {
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
