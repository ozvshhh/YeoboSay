import {
  BadGatewayException,
  BadRequestException,
  ConflictException,
  Injectable,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import {
  CallSessionMode,
  CallSessionStatus,
  ConversationRole,
  ConversationStep,
  ConversationTurnStatus,
} from '@prisma/client';
import type { CallSession, ConversationTurn } from '@prisma/client';
import { DemoLoggerService } from '../common/demo-logger.service';
import { OpenAiService } from '../openai/openai.service';
import type { AssistantMessage } from '../openai/openai.service';
import { PrismaService } from '../prisma/prisma.service';
import {
  AutoTurnNextAction,
  AutoVoiceTurnResponseDto,
  AutoVoiceTurnUploadDto,
} from './auto-voice-turn.dto';
import {
  AudioPolicyResponseDto,
  CallSessionResponseDto,
  ConversationPolicyResponseDto,
} from './call-session-response.dto';
import {
  ConversationTurnListResponseDto,
  ConversationTurnResponseDto,
} from './conversation-turn-response.dto';
import { CreateCallSessionDto } from './create-call-session.dto';
import { VoiceTurnResponseDto } from './voice-turn-response.dto';

const CALL_SESSION_DURATION_MS = 10 * 60 * 1000;
const TARGET_AUTO_TURN_COUNT = 5;
const RECENT_TURN_LIMIT = 12;
const SUPPORTED_AUDIO_MIME_TYPE = 'audio/mp4';
const SUPPORTED_AUTO_AUDIO_MIME_TYPES = new Set([
  'audio/mp4',
  'audio/m4a',
  'audio/wav',
  'audio/x-wav',
  'audio/mpeg',
  'audio/mp3',
]);
const ASSISTANT_FAILURE_MESSAGE =
  '응답을 만드는 중 문제가 생겼어요. 잠시 후 다시 말해 주세요.';
const AUTO_CONVERSATION_RETRY_PROMPT =
  '죄송해요, 잘 못 들었어요. 다시 한 번 말씀해 주시겠어요?';
const AUTO_CONVERSATION_FIRST_GREETING =
  '안녕하세요 왕송길 어르신 AI통화 서비스 세요입니다!';
const AUTO_CONVERSATION_NO_RESPONSE_PROMPT = '여보세요? 제 말 들리세요?';
const AUTO_CONVERSATION_MAX_DURATION_CLOSING =
  '어르신 아쉽지만 오늘 통화는 여기까지에요.';
const AUTO_CONVERSATION_DEFAULT_ASSISTANT_TEXT =
  '말씀을 잘 받았어요. 조금 더 이야기해 볼게요.';
const TERMINAL_TURN_STATUSES = new Set<ConversationTurnStatus>([
  ConversationTurnStatus.COMPLETED,
  ConversationTurnStatus.FAILED_STT,
  ConversationTurnStatus.FAILED_LLM,
  ConversationTurnStatus.FAILED_TTS,
  ConversationTurnStatus.FAILED_UNKNOWN,
]);

const AUTO_AUDIO_POLICY: AudioPolicyResponseDto = {
  silenceTimeoutMs: 3000,
  maxUtteranceMs: 30000,
  uploadMimeType: SUPPORTED_AUDIO_MIME_TYPE,
  bargeInEnabled: true,
};

const AUTO_CONVERSATION_POLICY: ConversationPolicyResponseDto = {
  firstGreetingText: AUTO_CONVERSATION_FIRST_GREETING,
  noResponsePromptText: AUTO_CONVERSATION_NO_RESPONSE_PROMPT,
  maxDurationClosingText: AUTO_CONVERSATION_MAX_DURATION_CLOSING,
  targetTurnCount: TARGET_AUTO_TURN_COUNT,
  maxDurationSeconds: CALL_SESSION_DURATION_MS / 1000,
};

type FirstGreetingAudio = {
  mimeType: string;
  base64: string;
};

type AutoAudioMetadata = {
  filename: string;
  mimeType: string;
  size: number;
};

type AutoVoiceTurnResponseOptions = {
  audioMimeType?: string | null;
  audioBase64?: string | null;
  nextAction?: AutoTurnNextAction;
  failed?: boolean;
};

const RISK_KEYWORDS: Array<{ type: string; keywords: string[] }> = [
  {
    type: 'SELF_HARM',
    keywords: ['죽고 싶', '살고 싶지', '자해', '극단적 선택'],
  },
  {
    type: 'MEDICAL_EMERGENCY',
    keywords: ['숨을 못', '가슴이 아파', '쓰러졌', '119', '응급실'],
  },
  {
    type: 'HELP_REQUEST',
    keywords: ['도와줘', '도움이 필요', '살려줘', '살려'],
  },
  {
    type: 'EMOTIONAL_DISTRESS',
    keywords: ['외로', '우울', '불안', '힘들', '괴로'],
  },
];

@Injectable()
export class CallSessionsService {
  private readonly logger = new Logger(CallSessionsService.name);

  constructor(
    private readonly prisma: PrismaService,
    private readonly openAiService: OpenAiService,
    private readonly demoLogger: DemoLoggerService,
  ) {}

  async create(
    dto: CreateCallSessionDto = {},
  ): Promise<CallSessionResponseDto> {
    const startedAt = new Date();
    const expiresAt = new Date(startedAt.getTime() + CALL_SESSION_DURATION_MS);
    const mode = this.toPrismaSessionMode(dto.mode);
    const isAutoConversation = mode === CallSessionMode.AUTO_CONVERSATION;

    const session = await this.prisma.callSession.create({
      data: {
        mode,
        currentStep: isAutoConversation ? ConversationStep.GREETING : null,
        targetTurnCount: TARGET_AUTO_TURN_COUNT,
        startedAt,
        expiresAt,
      },
    });

    if (isAutoConversation) {
      await this.prisma.conversationTurn.create({
        data: {
          callSessionId: session.id,
          role: ConversationRole.ASSISTANT,
          text: AUTO_CONVERSATION_FIRST_GREETING,
          status: ConversationTurnStatus.COMPLETED,
          conversationStep: ConversationStep.GREETING,
          completedAt: startedAt,
        },
      });
    }

    this.demoLogger.callSessionCreated(session.id, session.expiresAt);

    const firstGreetingAudio = isAutoConversation
      ? await this.createFirstGreetingAudio(session.id)
      : null;

    return this.toCallSessionResponse(
      session,
      isAutoConversation,
      firstGreetingAudio,
    );
  }

  async findOne(id: string): Promise<CallSessionResponseDto> {
    const session = await this.prisma.callSession.findUnique({
      where: { id },
    });

    if (!session) {
      throw new NotFoundException('Call session not found.');
    }

    return this.toCallSessionResponse(session);
  }

  async findTurns(id: string): Promise<ConversationTurnListResponseDto> {
    await this.ensureSessionExists(id);

    const turns = await this.prisma.conversationTurn.findMany({
      where: { callSessionId: id },
      orderBy: { createdAt: 'asc' },
    });

    return {
      callSessionId: id,
      turns: turns.map((turn) => this.toConversationTurnResponse(turn)),
    };
  }

  async processAudioTurn(
    id: string,
    audio?: Express.Multer.File,
  ): Promise<VoiceTurnResponseDto> {
    const session = await this.prisma.callSession.findUnique({
      where: { id },
    });

    if (!session) {
      throw new NotFoundException('Call session not found.');
    }

    if (session.status === CallSessionStatus.ENDED) {
      throw new ConflictException('Call session is already ended.');
    }

    if (session.expiresAt.getTime() <= Date.now()) {
      throw new ConflictException('Call session is expired.');
    }

    this.validateAudioUpload(audio);
    this.demoLogger.voiceTurnStarted(
      id,
      audio.originalname || 'turn.m4a',
      audio.mimetype,
      audio.size ?? audio.buffer.length,
    );

    const userText = await this.transcribeAudio(audio);
    const riskType = this.detectRiskType(userText);
    const riskFlag = Boolean(riskType);

    if (riskFlag) {
      this.logger.warn(
        `Risk speech detected: callSessionId=${id} riskType=${riskType}`,
      );
    }
    this.demoLogger.userTextTranscribed(id, userText, {
      riskFlag,
      riskType,
    });

    await this.prisma.conversationTurn.create({
      data: {
        callSessionId: id,
        role: ConversationRole.USER,
        text: userText,
        riskFlag,
        riskType,
      },
    });

    const messages = await this.buildAssistantMessages(id);

    try {
      const assistantText =
        await this.openAiService.generateAssistantText(messages);
      const assistantAudio =
        await this.openAiService.synthesizeSpeech(assistantText);
      this.demoLogger.assistantTextGenerated(id, assistantText, false);

      await this.prisma.conversationTurn.create({
        data: {
          callSessionId: id,
          role: ConversationRole.ASSISTANT,
          text: assistantText,
        },
      });

      return {
        callSessionId: id,
        userText,
        assistantText,
        audioMimeType: 'audio/mpeg',
        audioBase64: assistantAudio.toString('base64'),
        failed: false,
        riskFlag,
        riskType,
      };
    } catch (error) {
      this.logger.error(
        `Assistant response generation failed: callSessionId=${id}`,
        error instanceof Error ? error.stack : undefined,
      );
      this.demoLogger.assistantGenerationFailed(id, error);
      this.demoLogger.assistantTextGenerated(
        id,
        ASSISTANT_FAILURE_MESSAGE,
        true,
      );

      await this.prisma.conversationTurn.create({
        data: {
          callSessionId: id,
          role: ConversationRole.ASSISTANT,
          text: ASSISTANT_FAILURE_MESSAGE,
          failed: true,
        },
      });

      return {
        callSessionId: id,
        userText,
        assistantText: ASSISTANT_FAILURE_MESSAGE,
        audioMimeType: null,
        audioBase64: null,
        failed: true,
        riskFlag,
        riskType,
      };
    }
  }

  async processAutoAudioTurn(
    id: string,
    dto: AutoVoiceTurnUploadDto,
    audio?: Express.Multer.File,
  ): Promise<AutoVoiceTurnResponseDto> {
    const session = await this.prisma.callSession.findUnique({
      where: { id },
    });

    if (!session) {
      throw new NotFoundException('Call session not found.');
    }

    if (session.mode !== CallSessionMode.AUTO_CONVERSATION) {
      throw new BadRequestException(
        'Automatic turns require an auto conversation session.',
      );
    }

    if (!this.isAutoTurnSessionProcessable(session.status)) {
      throw new ConflictException(
        `Call session cannot process an automatic turn while ${session.status}.`,
      );
    }

    if (session.expiresAt.getTime() <= Date.now()) {
      throw new ConflictException('Call session is expired.');
    }

    this.validateAutoAudioUpload(audio, dto);
    const audioMetadata = this.resolveAutoAudioMetadata(audio, dto);

    const existingTurn = await this.prisma.conversationTurn.findFirst({
      where: {
        callSessionId: id,
        clientTurnId: dto.clientTurnId,
        role: ConversationRole.USER,
      },
    });

    if (existingTurn) {
      if (TERMINAL_TURN_STATUSES.has(existingTurn.status)) {
        const assistantTurn = await this.findAssistantTurnAfter(existingTurn);
        return this.toAutoVoiceTurnResponse(existingTurn, assistantTurn);
      }

      throw new ConflictException(
        'Automatic turn with the same clientTurnId is still processing.',
      );
    }

    this.demoLogger.voiceTurnStarted(
      id,
      audioMetadata.filename,
      audioMetadata.mimeType,
      audioMetadata.size,
    );

    const conversationStep = this.toPrismaConversationStep(
      dto.conversationStep,
    );
    const userTurn = await this.prisma.conversationTurn.create({
      data: {
        callSessionId: id,
        clientTurnId: dto.clientTurnId,
        role: ConversationRole.USER,
        text: '',
        status: ConversationTurnStatus.UPLOADED,
        conversationStep,
        bargeIn: dto.bargeIn === 'true',
      },
    });

    await this.prisma.callSession.update({
      where: { id },
      data: { status: CallSessionStatus.PROCESSING_TURN },
    });

    await this.prisma.conversationTurn.update({
      where: { id: userTurn.id },
      data: {
        status: ConversationTurnStatus.TRANSCRIBING,
      },
    });

    let userText: string;

    try {
      userText = await this.openAiService.transcribeAudio(
        audio.buffer,
        audioMetadata.filename,
        audioMetadata.mimeType,
      );
    } catch (error) {
      this.logger.error(
        `Automatic turn transcription failed: callSessionId=${id} clientTurnId=${dto.clientTurnId}`,
        error instanceof Error ? error.stack : undefined,
      );

      const failedUserTurn = await this.prisma.conversationTurn.update({
        where: { id: userTurn.id },
        data: {
          status: ConversationTurnStatus.FAILED_STT,
          failed: true,
          errorCode: 'STT_FAILED',
          completedAt: new Date(),
        },
      });
      const retryTurn = await this.createAutoRetryAssistantTurn(
        id,
        conversationStep,
        true,
      );

      await this.prisma.callSession.update({
        where: { id },
        data: { status: CallSessionStatus.WAITING_FOR_USER },
      });

      this.demoLogger.assistantGenerationFailed(id, error);
      this.demoLogger.assistantTextGenerated(
        id,
        AUTO_CONVERSATION_RETRY_PROMPT,
        true,
      );

      return this.toAutoVoiceTurnResponse(failedUserTurn, retryTurn, {
        nextAction: 'listen_again',
        failed: true,
      });
    }

    if (!userText) {
      const emptyUserTurn = await this.prisma.conversationTurn.update({
        where: { id: userTurn.id },
        data: {
          status: ConversationTurnStatus.FAILED_STT,
          failed: true,
          errorCode: 'EMPTY_TRANSCRIPTION',
          completedAt: new Date(),
        },
      });
      const retryTurn = await this.createAutoRetryAssistantTurn(
        id,
        conversationStep,
        false,
      );

      await this.prisma.callSession.update({
        where: { id },
        data: { status: CallSessionStatus.WAITING_FOR_USER },
      });

      this.demoLogger.assistantTextGenerated(
        id,
        AUTO_CONVERSATION_RETRY_PROMPT,
        false,
      );

      return this.toAutoVoiceTurnResponse(emptyUserTurn, retryTurn, {
        nextAction: 'listen_again',
        failed: false,
      });
    }

    const riskType = this.detectRiskType(userText);
    const riskFlag = Boolean(riskType);

    if (riskFlag) {
      this.logger.warn(
        `Risk speech detected: callSessionId=${id} riskType=${riskType}`,
      );
    }

    this.demoLogger.userTextTranscribed(id, userText, {
      riskFlag,
      riskType,
    });

    const transcribedUserTurn = await this.prisma.conversationTurn.update({
      where: { id: userTurn.id },
      data: {
        text: userText,
        status: ConversationTurnStatus.TRANSCRIBED,
        riskFlag,
        riskType,
      },
    });

    await this.prisma.conversationTurn.update({
      where: { id: transcribedUserTurn.id },
      data: { status: ConversationTurnStatus.RESPONDING },
    });

    const assistantConversationStep =
      this.nextConversationStep(conversationStep ?? session.currentStep) ??
      conversationStep ??
      session.currentStep ??
      ConversationStep.FREE_TALK;
    const messages = await this.buildAssistantMessages(id);
    let assistantText: string;

    try {
      assistantText = await this.openAiService.generateAssistantText(
        messages,
        this.buildAutoConversationInstructions(
          conversationStep ?? session.currentStep,
          assistantConversationStep,
          userText,
        ),
      );
    } catch (error) {
      this.logger.error(
        `Automatic assistant text generation failed: callSessionId=${id} clientTurnId=${dto.clientTurnId}`,
        error instanceof Error ? error.stack : undefined,
      );

      const failedUserTurn = await this.prisma.conversationTurn.update({
        where: { id: transcribedUserTurn.id },
        data: {
          status: ConversationTurnStatus.FAILED_LLM,
          failed: true,
          errorCode: 'LLM_FAILED',
          completedAt: new Date(),
        },
      });
      const failureTurn = await this.createAutoAssistantTurn({
        callSessionId: id,
        text: ASSISTANT_FAILURE_MESSAGE,
        conversationStep: assistantConversationStep,
        failed: true,
      });

      await this.prisma.callSession.update({
        where: { id },
        data: { status: CallSessionStatus.WAITING_FOR_USER },
      });

      this.demoLogger.assistantGenerationFailed(id, error);
      this.demoLogger.assistantTextGenerated(
        id,
        ASSISTANT_FAILURE_MESSAGE,
        true,
      );

      return this.toAutoVoiceTurnResponse(failedUserTurn, failureTurn, {
        nextAction: 'listen_again',
        failed: true,
      });
    }

    await this.prisma.conversationTurn.update({
      where: { id: transcribedUserTurn.id },
      data: { status: ConversationTurnStatus.RESPONDED },
    });

    await this.prisma.conversationTurn.update({
      where: { id: transcribedUserTurn.id },
      data: { status: ConversationTurnStatus.SYNTHESIZING },
    });

    let assistantAudio: Buffer;

    try {
      assistantAudio = await this.openAiService.synthesizeSpeech(assistantText);
    } catch (error) {
      this.logger.error(
        `Automatic assistant speech generation failed: callSessionId=${id} clientTurnId=${dto.clientTurnId}`,
        error instanceof Error ? error.stack : undefined,
      );

      const failedUserTurn = await this.prisma.conversationTurn.update({
        where: { id: transcribedUserTurn.id },
        data: {
          status: ConversationTurnStatus.FAILED_TTS,
          failed: true,
          errorCode: 'TTS_FAILED',
          completedAt: new Date(),
        },
      });
      const failedAssistantTurn = await this.createAutoAssistantTurn({
        callSessionId: id,
        text: assistantText,
        conversationStep: assistantConversationStep,
        failed: true,
      });

      await this.prisma.callSession.update({
        where: { id },
        data: { status: CallSessionStatus.WAITING_FOR_USER },
      });

      this.demoLogger.assistantGenerationFailed(id, error);
      this.demoLogger.assistantTextGenerated(id, assistantText, true);

      return this.toAutoVoiceTurnResponse(failedUserTurn, failedAssistantTurn, {
        nextAction: 'listen_again',
        failed: true,
      });
    }

    const completedUserTurn = await this.prisma.conversationTurn.update({
      where: { id: transcribedUserTurn.id },
      data: {
        status: ConversationTurnStatus.COMPLETED,
        completedAt: new Date(),
      },
    });

    const assistantTurn = await this.createAutoAssistantTurn({
      callSessionId: id,
      text: assistantText,
      conversationStep: assistantConversationStep,
      failed: false,
    });

    await this.prisma.callSession.update({
      where: { id },
      data: {
        status: CallSessionStatus.AI_SPEAKING,
        currentStep: assistantConversationStep,
        turnCount: { increment: 1 },
        riskFlag: riskFlag ? true : undefined,
        riskType: riskType ?? undefined,
      },
    });

    this.demoLogger.assistantTextGenerated(id, assistantText, false);

    return this.toAutoVoiceTurnResponse(completedUserTurn, assistantTurn, {
      audioMimeType: 'audio/mpeg',
      audioBase64: assistantAudio.toString('base64'),
      nextAction: 'play_audio',
      failed: false,
    });
  }

  async end(id: string): Promise<CallSessionResponseDto> {
    const session = await this.prisma.callSession.findUnique({
      where: { id },
    });

    if (!session) {
      throw new NotFoundException('Call session not found.');
    }

    if (session.status === CallSessionStatus.ENDED) {
      throw new ConflictException('Call session is already ended.');
    }

    const endedSession = await this.prisma.callSession.update({
      where: { id },
      data: {
        status: CallSessionStatus.ENDED,
        endedAt: new Date(),
      },
    });

    this.demoLogger.callSessionEnded(endedSession.id, endedSession.endedAt);

    return this.toCallSessionResponse(endedSession);
  }

  private async ensureSessionExists(id: string): Promise<void> {
    const session = await this.prisma.callSession.findUnique({
      where: { id },
      select: { id: true },
    });

    if (!session) {
      throw new NotFoundException('Call session not found.');
    }
  }

  private validateAudioUpload(
    audio?: Express.Multer.File,
  ): asserts audio is Express.Multer.File {
    if (!audio) {
      throw new BadRequestException('Audio file is required.');
    }

    if (!audio.buffer?.length) {
      throw new BadRequestException('Audio file is empty.');
    }

    if (audio.mimetype !== SUPPORTED_AUDIO_MIME_TYPE) {
      throw new BadRequestException(
        'Only audio/mp4 M4A uploads are supported.',
      );
    }
  }

  private validateAutoAudioUpload(
    audio: Express.Multer.File | undefined,
    dto: AutoVoiceTurnUploadDto,
  ): asserts audio is Express.Multer.File {
    if (!audio) {
      throw new BadRequestException('Audio file is required.');
    }

    if (!audio.buffer?.length) {
      throw new BadRequestException('Audio file is empty.');
    }

    const requestedMimeType = dto.mimeType || audio.mimetype;

    if (!SUPPORTED_AUTO_AUDIO_MIME_TYPES.has(requestedMimeType)) {
      throw new BadRequestException(
        'Only audio/mp4, audio/wav, and audio/mpeg uploads are supported.',
      );
    }
  }

  private resolveAutoAudioMetadata(
    audio: Express.Multer.File,
    dto: AutoVoiceTurnUploadDto,
  ): AutoAudioMetadata {
    return {
      filename: audio.originalname || 'auto-turn.m4a',
      mimeType: dto.mimeType || audio.mimetype,
      size: audio.size ?? audio.buffer.length,
    };
  }

  private async transcribeAudio(audio: Express.Multer.File): Promise<string> {
    try {
      const text = await this.openAiService.transcribeAudio(
        audio.buffer,
        audio.originalname || 'turn.m4a',
        audio.mimetype,
      );

      if (!text) {
        throw new BadRequestException('Audio transcription was empty.');
      }

      return text;
    } catch (error) {
      if (error instanceof BadRequestException) {
        throw error;
      }

      this.logger.error(
        'Audio transcription failed.',
        error instanceof Error ? error.stack : undefined,
      );
      throw new BadGatewayException('Failed to transcribe audio.');
    }
  }

  private detectRiskType(text: string): string | null {
    const normalizedText = text.toLowerCase();
    const matchedRisk = RISK_KEYWORDS.find((risk) =>
      risk.keywords.some((keyword) =>
        normalizedText.includes(keyword.toLowerCase()),
      ),
    );

    return matchedRisk?.type ?? null;
  }

  private isAutoTurnSessionProcessable(status: CallSessionStatus): boolean {
    const processableStatuses: CallSessionStatus[] = [
      CallSessionStatus.ACTIVE,
      CallSessionStatus.WAITING_FOR_USER,
      CallSessionStatus.AI_SPEAKING,
    ];

    return processableStatuses.includes(status);
  }

  private toPrismaConversationStep(
    step?: string,
  ): ConversationStep | undefined {
    if (!step) return undefined;

    const normalizedStep = step.toUpperCase();
    const validStep = Object.values(ConversationStep).find(
      (value) => value === normalizedStep,
    );

    return validStep;
  }

  private async findAssistantTurnAfter(
    userTurn: ConversationTurn,
  ): Promise<ConversationTurn | null> {
    return this.prisma.conversationTurn.findFirst({
      where: {
        callSessionId: userTurn.callSessionId,
        role: ConversationRole.ASSISTANT,
        createdAt: { gte: userTurn.createdAt },
      },
      orderBy: { createdAt: 'asc' },
    });
  }

  private async createAutoRetryAssistantTurn(
    callSessionId: string,
    conversationStep: ConversationStep | undefined,
    failed: boolean,
  ): Promise<ConversationTurn> {
    return this.createAutoAssistantTurn({
      callSessionId,
      text: AUTO_CONVERSATION_RETRY_PROMPT,
      conversationStep,
      failed,
    });
  }

  private async createAutoAssistantTurn(params: {
    callSessionId: string;
    text: string;
    conversationStep: ConversationStep | undefined;
    failed: boolean;
  }): Promise<ConversationTurn> {
    return this.prisma.conversationTurn.create({
      data: {
        callSessionId: params.callSessionId,
        role: ConversationRole.ASSISTANT,
        text: params.text,
        status: ConversationTurnStatus.COMPLETED,
        conversationStep: params.conversationStep,
        failed: params.failed,
        completedAt: new Date(),
      },
    });
  }

  private nextConversationStep(
    currentStep?: ConversationStep | null,
  ): ConversationStep {
    switch (currentStep) {
      case ConversationStep.GREETING:
        return ConversationStep.WELLBEING;
      case ConversationStep.WELLBEING:
        return ConversationStep.MEAL;
      case ConversationStep.MEAL:
        return ConversationStep.HEALTH;
      case ConversationStep.HEALTH:
        return ConversationStep.MEDICATION;
      case ConversationStep.MEDICATION:
        return ConversationStep.SLEEP;
      case ConversationStep.SLEEP:
        return ConversationStep.SCHEDULE;
      case ConversationStep.SCHEDULE:
        return ConversationStep.MOOD;
      case ConversationStep.MOOD:
      case ConversationStep.FREE_TALK:
      case ConversationStep.CLOSING:
      default:
        return ConversationStep.FREE_TALK;
    }
  }

  private buildAutoConversationInstructions(
    userStep: ConversationStep | null | undefined,
    assistantStep: ConversationStep,
    userText: string,
  ): string {
    return [
      'This is an automatic phone conversation with an elderly Korean user.',
      'Speak like a polite, friendly young male companion.',
      'Use simple Korean, large-print-caption friendly sentences, and no markdown.',
      'If the answer was too short to understand, ask once more naturally. If it was enough, move to the next care-check topic.',
      `The user just answered the ${this.describeConversationStep(userStep)} step: "${userText}".`,
      `Your next care-check step is ${this.describeConversationStep(assistantStep)}.`,
      `Ask or respond using this focus: ${this.getConversationStepPrompt(assistantStep)}`,
    ].join(' ');
  }

  private describeConversationStep(step?: ConversationStep | null): string {
    switch (step) {
      case ConversationStep.GREETING:
        return '첫 인사';
      case ConversationStep.WELLBEING:
        return '오늘 하루 안부';
      case ConversationStep.MEAL:
        return '식사 확인';
      case ConversationStep.HEALTH:
        return '건강 상태 확인';
      case ConversationStep.MEDICATION:
        return '복약 확인';
      case ConversationStep.SLEEP:
        return '수면 확인';
      case ConversationStep.SCHEDULE:
        return '일정 확인';
      case ConversationStep.MOOD:
        return '기분 확인';
      case ConversationStep.CLOSING:
        return '마무리';
      case ConversationStep.FREE_TALK:
      default:
        return '자유 대화';
    }
  }

  private getConversationStepPrompt(step: ConversationStep): string {
    switch (step) {
      case ConversationStep.WELLBEING:
        return '오늘 하루는 어떠셨는지 다정하게 물어보세요.';
      case ConversationStep.MEAL:
        return '오늘 식사는 잘 챙겨 드셨는지 물어보세요.';
      case ConversationStep.HEALTH:
        return '아픈 곳이나 불편한 곳이 있는지 물어보세요.';
      case ConversationStep.MEDICATION:
        return '드셔야 할 약은 잘 챙겨 드셨는지 물어보세요.';
      case ConversationStep.SLEEP:
        return '잠은 편하게 주무셨는지 물어보세요.';
      case ConversationStep.SCHEDULE:
        return '오늘이나 내일 챙길 일정이 있는지 물어보세요.';
      case ConversationStep.MOOD:
        return '요즘 마음이나 기분은 어떠신지 물어보세요.';
      case ConversationStep.CLOSING:
        return '대화를 따뜻하게 마무리하세요.';
      case ConversationStep.GREETING:
      case ConversationStep.FREE_TALK:
      default:
        return '사용자의 말에 공감하고 짧은 후속 질문을 하세요.';
    }
  }

  private async buildAssistantMessages(
    callSessionId: string,
  ): Promise<AssistantMessage[]> {
    const recentTurns = await this.prisma.conversationTurn.findMany({
      where: { callSessionId },
      orderBy: { createdAt: 'desc' },
      take: RECENT_TURN_LIMIT,
    });

    return recentTurns.reverse().map((turn) => ({
      role: turn.role === ConversationRole.USER ? 'user' : 'assistant',
      content: turn.text,
    }));
  }

  private toPrismaSessionMode(
    mode: CreateCallSessionDto['mode'],
  ): CallSessionMode {
    return mode === 'auto_conversation'
      ? CallSessionMode.AUTO_CONVERSATION
      : CallSessionMode.MANUAL_RECORDING;
  }

  private toApiSessionMode(
    mode?: CallSessionMode,
  ): 'manual_recording' | 'auto_conversation' {
    return mode === CallSessionMode.AUTO_CONVERSATION
      ? 'auto_conversation'
      : 'manual_recording';
  }

  private toApiConversationStep(step?: ConversationStep | null): string | null {
    return step ? step.toLowerCase() : null;
  }

  private toCallSessionResponse(
    session: CallSession,
    includePolicies = false,
    firstGreetingAudio: FirstGreetingAudio | null = null,
  ): CallSessionResponseDto {
    const sessionDetails = session as CallSession & {
      mode?: CallSessionMode;
      currentStep?: ConversationStep | null;
      turnCount?: number;
      targetTurnCount?: number;
      riskFlag?: boolean;
      riskType?: string | null;
    };

    return {
      id: session.id,
      status: session.status,
      mode: this.toApiSessionMode(sessionDetails.mode),
      currentStep: this.toApiConversationStep(sessionDetails.currentStep),
      turnCount: sessionDetails.turnCount ?? 0,
      targetTurnCount: sessionDetails.targetTurnCount ?? TARGET_AUTO_TURN_COUNT,
      riskFlag: sessionDetails.riskFlag ?? false,
      riskType: sessionDetails.riskType ?? null,
      startedAt: session.startedAt.toISOString(),
      endedAt: session.endedAt?.toISOString() ?? null,
      expiresAt: session.expiresAt.toISOString(),
      ...(includePolicies
        ? {
            audioPolicy: AUTO_AUDIO_POLICY,
            conversationPolicy: {
              ...AUTO_CONVERSATION_POLICY,
              firstGreetingAudioMimeType: firstGreetingAudio?.mimeType ?? null,
              firstGreetingAudioBase64: firstGreetingAudio?.base64 ?? null,
            },
          }
        : {}),
    };
  }

  private async createFirstGreetingAudio(
    callSessionId: string,
  ): Promise<FirstGreetingAudio> {
    try {
      const audio = await this.openAiService.synthesizeSpeech(
        AUTO_CONVERSATION_FIRST_GREETING,
      );
      return {
        mimeType: 'audio/mpeg',
        base64: audio.toString('base64'),
      };
    } catch (error) {
      this.logger.warn(
        `First greeting TTS generation failed: callSessionId=${callSessionId}`,
        error instanceof Error ? error.stack : undefined,
      );
      throw new BadGatewayException('First greeting audio generation failed.');
    }
  }

  private toConversationTurnResponse(
    turn: ConversationTurn,
  ): ConversationTurnResponseDto {
    return {
      id: turn.id,
      role: turn.role,
      text: turn.text,
      failed: turn.failed,
      riskFlag: turn.riskFlag,
      riskType: turn.riskType,
      createdAt: turn.createdAt.toISOString(),
    };
  }

  private toAutoVoiceTurnResponse(
    userTurn: ConversationTurn,
    assistantTurn: ConversationTurn | null,
    options: AutoVoiceTurnResponseOptions = {},
  ): AutoVoiceTurnResponseDto {
    const failed =
      options.failed ?? (userTurn.failed || Boolean(assistantTurn?.failed));

    return {
      turnId: userTurn.id,
      clientTurnId: userTurn.clientTurnId ?? '',
      sessionId: userTurn.callSessionId,
      userText: userTurn.text,
      assistantText:
        assistantTurn?.text ?? AUTO_CONVERSATION_DEFAULT_ASSISTANT_TEXT,
      audioMimeType: options.audioMimeType ?? null,
      audioBase64: options.audioBase64 ?? null,
      conversationStep: this.toApiConversationStep(
        assistantTurn?.conversationStep ?? userTurn.conversationStep,
      ),
      nextAction:
        options.nextAction ?? (failed ? 'listen_again' : 'play_audio'),
      failed,
      riskFlag: userTurn.riskFlag || Boolean(assistantTurn?.riskFlag),
      riskType: userTurn.riskType ?? assistantTurn?.riskType ?? null,
    };
  }
}
