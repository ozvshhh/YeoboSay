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
const ASSISTANT_FAILURE_MESSAGE =
  '응답을 만드는 중 문제가 생겼어요. 잠시 후 다시 말해 주세요.';
const AUTO_CONVERSATION_FIRST_GREETING =
  '어르신 세요가 아침인사 드립니다! 잠은 편히 주무셨나요? 오늘의 기분은 어떠세요?';
const AUTO_CONVERSATION_NO_RESPONSE_PROMPT = '여보세요? 제 말 들리세요?';
const AUTO_CONVERSATION_MAX_DURATION_CLOSING =
  '어르신 아쉽지만 오늘 통화는 여기까지에요.';
const AUTO_TURN_MOCK_USER_TEXT = '자동 발화가 접수됐어요.';
const AUTO_TURN_MOCK_ASSISTANT_TEXT =
  '말씀을 잘 받았어요. 다음 단계에서 실제 AI 답변으로 연결할게요.';
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

    const firstGreetingAudio = await this.createFirstGreetingAudio(session.id);

    return this.toCallSessionResponse(session, true, firstGreetingAudio);
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

    this.validateAudioUpload(audio);

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
      audio.originalname || 'auto-turn.m4a',
      audio.mimetype,
      audio.size ?? audio.buffer.length,
    );

    const conversationStep = this.toPrismaConversationStep(
      dto.conversationStep,
    );
    const userTurn = await this.prisma.conversationTurn.create({
      data: {
        callSessionId: id,
        clientTurnId: dto.clientTurnId,
        role: ConversationRole.USER,
        text: AUTO_TURN_MOCK_USER_TEXT,
        status: ConversationTurnStatus.UPLOADED,
        conversationStep,
        bargeIn: dto.bargeIn === 'true',
      },
    });

    await this.prisma.callSession.update({
      where: { id },
      data: { status: CallSessionStatus.PROCESSING_TURN },
    });

    const completedUserTurn = await this.prisma.conversationTurn.update({
      where: { id: userTurn.id },
      data: {
        status: ConversationTurnStatus.COMPLETED,
        completedAt: new Date(),
      },
    });

    const assistantTurn = await this.prisma.conversationTurn.create({
      data: {
        callSessionId: id,
        role: ConversationRole.ASSISTANT,
        text: AUTO_TURN_MOCK_ASSISTANT_TEXT,
        status: ConversationTurnStatus.COMPLETED,
        conversationStep,
        completedAt: new Date(),
      },
    });

    await this.prisma.callSession.update({
      where: { id },
      data: {
        status: CallSessionStatus.AI_SPEAKING,
        currentStep: conversationStep ?? session.currentStep,
        turnCount: { increment: 1 },
      },
    });

    this.demoLogger.assistantTextGenerated(
      id,
      AUTO_TURN_MOCK_ASSISTANT_TEXT,
      false,
    );

    return this.toAutoVoiceTurnResponse(completedUserTurn, assistantTurn);
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
  ): AutoVoiceTurnResponseDto {
    const failed = userTurn.failed || Boolean(assistantTurn?.failed);

    return {
      turnId: userTurn.id,
      clientTurnId: userTurn.clientTurnId ?? '',
      sessionId: userTurn.callSessionId,
      userText: userTurn.text,
      assistantText: assistantTurn?.text ?? AUTO_TURN_MOCK_ASSISTANT_TEXT,
      audioMimeType: null,
      audioBase64: null,
      conversationStep: this.toApiConversationStep(userTurn.conversationStep),
      nextAction: failed ? 'listen_again' : 'play_audio',
      failed,
      riskFlag: userTurn.riskFlag || Boolean(assistantTurn?.riskFlag),
      riskType: userTurn.riskType ?? assistantTurn?.riskType ?? null,
    };
  }
}
