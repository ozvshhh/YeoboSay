import {
  BadGatewayException,
  BadRequestException,
  ConflictException,
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
import { DemoLoggerService } from '../common/demo-logger.service';
import { OpenAiService } from '../openai/openai.service';
import { PrismaService } from '../prisma/prisma.service';
import { CallSessionsService } from './call-sessions.service';

const now = new Date('2026-05-16T05:00:00.000Z');
const expiresAt = new Date('2026-05-16T05:10:00.000Z');
const baseSession = {
  id: 'session-1',
  status: CallSessionStatus.ACTIVE,
  mode: CallSessionMode.MANUAL_RECORDING,
  currentStep: null,
  turnCount: 0,
  targetTurnCount: 5,
  riskFlag: false,
  riskType: null,
  endReason: null,
  startedAt: now,
  endedAt: null,
  expiresAt,
};
const baseSessionResponse = {
  id: 'session-1',
  status: 'ACTIVE',
  mode: 'manual_recording',
  currentStep: null,
  turnCount: 0,
  targetTurnCount: 5,
  riskFlag: false,
  riskType: null,
  startedAt: '2026-05-16T05:00:00.000Z',
  endedAt: null,
  expiresAt: '2026-05-16T05:10:00.000Z',
};

describe('CallSessionsService', () => {
  let service: CallSessionsService;
  let prisma: {
    callSession: {
      create: jest.Mock;
      findUnique: jest.Mock;
      update: jest.Mock;
    };
    conversationTurn: {
      create: jest.Mock;
      findFirst: jest.Mock;
      findMany: jest.Mock;
      update: jest.Mock;
    };
  };
  let openAiService: {
    transcribeAudio: jest.Mock;
    generateAssistantText: jest.Mock;
    synthesizeSpeech: jest.Mock;
  };
  let demoLogger: {
    callSessionCreated: jest.Mock;
    callSessionEnded: jest.Mock;
    voiceTurnStarted: jest.Mock;
    userTextTranscribed: jest.Mock;
    assistantTextGenerated: jest.Mock;
    assistantGenerationFailed: jest.Mock;
  };

  beforeEach(() => {
    jest.useFakeTimers();
    jest.setSystemTime(now);
    jest.spyOn(Logger.prototype, 'warn').mockImplementation();
    jest.spyOn(Logger.prototype, 'error').mockImplementation();

    prisma = {
      callSession: {
        create: jest.fn(),
        findUnique: jest.fn(),
        update: jest.fn(),
      },
      conversationTurn: {
        create: jest.fn(),
        findFirst: jest.fn(),
        findMany: jest.fn(),
        update: jest.fn(),
      },
    };
    openAiService = {
      transcribeAudio: jest.fn(),
      generateAssistantText: jest.fn(),
      synthesizeSpeech: jest.fn(),
    };
    demoLogger = {
      callSessionCreated: jest.fn(),
      callSessionEnded: jest.fn(),
      voiceTurnStarted: jest.fn(),
      userTextTranscribed: jest.fn(),
      assistantTextGenerated: jest.fn(),
      assistantGenerationFailed: jest.fn(),
    };

    service = new CallSessionsService(
      prisma as unknown as PrismaService,
      openAiService as unknown as OpenAiService,
      demoLogger as unknown as DemoLoggerService,
    );
  });

  afterEach(() => {
    jest.restoreAllMocks();
    jest.useRealTimers();
  });

  it('creates an active call session with a 10 minute expiration', async () => {
    prisma.callSession.create.mockResolvedValue(baseSession);

    await expect(service.create()).resolves.toEqual(baseSessionResponse);
    expect(prisma.callSession.create).toHaveBeenCalledWith({
      data: {
        mode: CallSessionMode.MANUAL_RECORDING,
        currentStep: null,
        targetTurnCount: 5,
        startedAt: now,
        expiresAt,
      },
    });
    expect(demoLogger.callSessionCreated).toHaveBeenCalledWith(
      'session-1',
      expiresAt,
    );
  });

  it('creates an auto conversation session with the first assistant greeting', async () => {
    prisma.callSession.create.mockResolvedValue({
      ...baseSession,
      mode: CallSessionMode.AUTO_CONVERSATION,
      currentStep: ConversationStep.GREETING,
    });
    prisma.conversationTurn.create.mockResolvedValue({});
    openAiService.synthesizeSpeech.mockResolvedValue(Buffer.from('greeting'));

    await expect(
      service.create({ mode: 'auto_conversation' }),
    ).resolves.toEqual({
      ...baseSessionResponse,
      mode: 'auto_conversation',
      currentStep: 'greeting',
      audioPolicy: {
        silenceTimeoutMs: 3000,
        maxUtteranceMs: 30000,
        uploadMimeType: 'audio/mp4',
        bargeInEnabled: true,
      },
      conversationPolicy: {
        firstGreetingText: '안녕하세요 왕송길 어르신 AI통화 서비스 세요입니다!',
        firstGreetingAudioMimeType: 'audio/mpeg',
        firstGreetingAudioBase64: Buffer.from('greeting').toString('base64'),
        noResponsePromptText: '여보세요? 제 말 들리세요?',
        maxDurationClosingText: '어르신 아쉽지만 오늘 통화는 여기까지에요.',
        targetTurnCount: 5,
        maxDurationSeconds: 600,
      },
    });
    expect(prisma.callSession.create).toHaveBeenCalledWith({
      data: {
        mode: CallSessionMode.AUTO_CONVERSATION,
        currentStep: ConversationStep.GREETING,
        targetTurnCount: 5,
        startedAt: now,
        expiresAt,
      },
    });
    expect(prisma.conversationTurn.create).toHaveBeenCalledWith({
      data: {
        callSessionId: 'session-1',
        role: ConversationRole.ASSISTANT,
        text: '안녕하세요 왕송길 어르신 AI통화 서비스 세요입니다!',
        status: ConversationTurnStatus.COMPLETED,
        conversationStep: ConversationStep.GREETING,
        completedAt: now,
      },
    });
    expect(openAiService.synthesizeSpeech).toHaveBeenCalledWith(
      '안녕하세요 왕송길 어르신 AI통화 서비스 세요입니다!',
    );
  });

  it('fails auto conversation session creation when first greeting audio fails', async () => {
    prisma.callSession.create.mockResolvedValue({
      ...baseSession,
      mode: CallSessionMode.AUTO_CONVERSATION,
      currentStep: ConversationStep.GREETING,
    });
    prisma.conversationTurn.create.mockResolvedValue({});
    openAiService.synthesizeSpeech.mockRejectedValue(new Error('TTS failed'));

    await expect(
      service.create({ mode: 'auto_conversation' }),
    ).rejects.toBeInstanceOf(BadGatewayException);
    expect(openAiService.synthesizeSpeech).toHaveBeenCalledWith(
      '안녕하세요 왕송길 어르신 AI통화 서비스 세요입니다!',
    );
  });

  it('returns an existing call session', async () => {
    prisma.callSession.findUnique.mockResolvedValue(baseSession);

    await expect(service.findOne('session-1')).resolves.toEqual(
      baseSessionResponse,
    );
  });

  it('throws when a call session does not exist', async () => {
    prisma.callSession.findUnique.mockResolvedValue(null);

    await expect(service.findOne('missing')).rejects.toBeInstanceOf(
      NotFoundException,
    );
  });

  it('returns conversation turns in storage order', async () => {
    prisma.callSession.findUnique.mockResolvedValue({ id: 'session-1' });
    prisma.conversationTurn.findMany.mockResolvedValue([
      {
        id: 'turn-1',
        callSessionId: 'session-1',
        role: ConversationRole.USER,
        text: '오늘 기분이 조금 외로워',
        failed: false,
        riskFlag: true,
        riskType: 'EMOTIONAL_DISTRESS',
        createdAt: now,
      },
    ]);

    await expect(service.findTurns('session-1')).resolves.toEqual({
      callSessionId: 'session-1',
      turns: [
        {
          id: 'turn-1',
          role: 'USER',
          text: '오늘 기분이 조금 외로워',
          failed: false,
          riskFlag: true,
          riskType: 'EMOTIONAL_DISTRESS',
          createdAt: '2026-05-16T05:00:00.000Z',
        },
      ],
    });
    expect(prisma.conversationTurn.findMany).toHaveBeenCalledWith({
      where: { callSessionId: 'session-1' },
      orderBy: { createdAt: 'asc' },
    });
  });

  it('processes an audio turn through transcription, assistant text, and speech', async () => {
    const audio = {
      buffer: Buffer.from('audio'),
      mimetype: 'audio/mp4',
      originalname: 'turn.m4a',
    } as Express.Multer.File;

    prisma.callSession.findUnique.mockResolvedValue({
      id: 'session-1',
      status: CallSessionStatus.ACTIVE,
      startedAt: now,
      endedAt: null,
      expiresAt,
    });
    openAiService.transcribeAudio.mockResolvedValue('오늘 산책을 했어요.');
    prisma.conversationTurn.create.mockResolvedValue({});
    prisma.conversationTurn.findMany.mockResolvedValue([
      {
        id: 'turn-1',
        callSessionId: 'session-1',
        role: ConversationRole.USER,
        text: '오늘 산책을 했어요.',
        failed: false,
        riskFlag: false,
        riskType: null,
        createdAt: now,
      },
    ]);
    openAiService.generateAssistantText.mockResolvedValue(
      '산책을 다녀오셨군요. 기분이 조금 가벼워지셨나요?',
    );
    openAiService.synthesizeSpeech.mockResolvedValue(Buffer.from('mp3'));

    await expect(service.processAudioTurn('session-1', audio)).resolves.toEqual(
      {
        callSessionId: 'session-1',
        userText: '오늘 산책을 했어요.',
        assistantText: '산책을 다녀오셨군요. 기분이 조금 가벼워지셨나요?',
        audioMimeType: 'audio/mpeg',
        audioBase64: Buffer.from('mp3').toString('base64'),
        failed: false,
        riskFlag: false,
        riskType: null,
      },
    );
    expect(openAiService.transcribeAudio).toHaveBeenCalledWith(
      audio.buffer,
      'turn.m4a',
      'audio/mp4',
    );
    expect(prisma.conversationTurn.create).toHaveBeenCalledTimes(2);
    expect(openAiService.generateAssistantText).toHaveBeenCalledWith([
      {
        role: 'user',
        content: '오늘 산책을 했어요.',
      },
    ]);
    expect(demoLogger.voiceTurnStarted).toHaveBeenCalledWith(
      'session-1',
      'turn.m4a',
      'audio/mp4',
      audio.buffer.length,
    );
    expect(demoLogger.userTextTranscribed).toHaveBeenCalledWith(
      'session-1',
      '오늘 산책을 했어요.',
      {
        riskFlag: false,
        riskType: null,
      },
    );
    expect(demoLogger.assistantTextGenerated).toHaveBeenCalledWith(
      'session-1',
      '산책을 다녀오셨군요. 기분이 조금 가벼워지셨나요?',
      false,
    );
  });

  it('stores risk metadata when processing risky speech', async () => {
    const audio = {
      buffer: Buffer.from('audio'),
      mimetype: 'audio/mp4',
      originalname: 'turn.m4a',
    } as Express.Multer.File;

    prisma.callSession.findUnique.mockResolvedValue({
      id: 'session-1',
      status: CallSessionStatus.ACTIVE,
      startedAt: now,
      endedAt: null,
      expiresAt,
    });
    openAiService.transcribeAudio.mockResolvedValue('너무 외로워요.');
    prisma.conversationTurn.create.mockResolvedValue({});
    prisma.conversationTurn.findMany.mockResolvedValue([]);
    openAiService.generateAssistantText.mockResolvedValue('많이 외로우셨군요.');
    openAiService.synthesizeSpeech.mockResolvedValue(Buffer.from('mp3'));

    await expect(service.processAudioTurn('session-1', audio)).resolves.toEqual(
      expect.objectContaining({
        riskFlag: true,
        riskType: 'EMOTIONAL_DISTRESS',
      }),
    );
    expect(prisma.conversationTurn.create).toHaveBeenCalledWith({
      data: {
        callSessionId: 'session-1',
        role: ConversationRole.USER,
        text: '너무 외로워요.',
        riskFlag: true,
        riskType: 'EMOTIONAL_DISTRESS',
      },
    });
  });

  it('returns a failed voice turn when assistant generation fails', async () => {
    const audio = {
      buffer: Buffer.from('audio'),
      mimetype: 'audio/mp4',
      originalname: 'turn.m4a',
    } as Express.Multer.File;

    prisma.callSession.findUnique.mockResolvedValue({
      id: 'session-1',
      status: CallSessionStatus.ACTIVE,
      startedAt: now,
      endedAt: null,
      expiresAt,
    });
    openAiService.transcribeAudio.mockResolvedValue('안녕하세요.');
    prisma.conversationTurn.create.mockResolvedValue({});
    prisma.conversationTurn.findMany.mockResolvedValue([]);
    openAiService.generateAssistantText.mockRejectedValue(
      new Error('OpenAI failed'),
    );

    await expect(service.processAudioTurn('session-1', audio)).resolves.toEqual(
      {
        callSessionId: 'session-1',
        userText: '안녕하세요.',
        assistantText:
          '응답을 만드는 중 문제가 생겼어요. 잠시 후 다시 말해 주세요.',
        audioMimeType: null,
        audioBase64: null,
        failed: true,
        riskFlag: false,
        riskType: null,
      },
    );
    expect(demoLogger.assistantGenerationFailed).toHaveBeenCalledWith(
      'session-1',
      expect.any(Error),
    );
    expect(demoLogger.assistantTextGenerated).toHaveBeenCalledWith(
      'session-1',
      '응답을 만드는 중 문제가 생겼어요. 잠시 후 다시 말해 주세요.',
      true,
    );
  });

  it('throws bad request for non-M4A audio uploads', async () => {
    prisma.callSession.findUnique.mockResolvedValue({
      id: 'session-1',
      status: CallSessionStatus.ACTIVE,
      startedAt: now,
      endedAt: null,
      expiresAt,
    });

    await expect(
      service.processAudioTurn('session-1', {
        buffer: Buffer.from('audio'),
        mimetype: 'audio/wav',
        originalname: 'turn.wav',
      } as Express.Multer.File),
    ).rejects.toBeInstanceOf(BadRequestException);
  });

  it('throws conflict when processing audio for an expired session', async () => {
    prisma.callSession.findUnique.mockResolvedValue({
      id: 'session-1',
      status: CallSessionStatus.ACTIVE,
      startedAt: new Date('2026-05-16T04:00:00.000Z'),
      endedAt: null,
      expiresAt: new Date('2026-05-16T04:10:00.000Z'),
    });

    await expect(
      service.processAudioTurn('session-1', {
        buffer: Buffer.from('audio'),
        mimetype: 'audio/mp4',
        originalname: 'turn.m4a',
      } as Express.Multer.File),
    ).rejects.toBeInstanceOf(ConflictException);
  });

  it('throws bad gateway when transcription fails', async () => {
    prisma.callSession.findUnique.mockResolvedValue({
      id: 'session-1',
      status: CallSessionStatus.ACTIVE,
      startedAt: now,
      endedAt: null,
      expiresAt,
    });
    openAiService.transcribeAudio.mockRejectedValue(new Error('OpenAI failed'));

    await expect(
      service.processAudioTurn('session-1', {
        buffer: Buffer.from('audio'),
        mimetype: 'audio/mp4',
        originalname: 'turn.m4a',
      } as Express.Multer.File),
    ).rejects.toBeInstanceOf(BadGatewayException);
  });

  it('stores an automatic turn with clientTurnId and returns a mock response', async () => {
    const audio = {
      buffer: Buffer.from('audio'),
      mimetype: 'audio/mp4',
      originalname: 'auto-turn.m4a',
    } as Express.Multer.File;
    const userTurn = {
      id: 'turn-user-1',
      callSessionId: 'session-1',
      clientTurnId: 'android-turn-1',
      role: ConversationRole.USER,
      text: '자동 발화가 접수됐어요.',
      status: ConversationTurnStatus.UPLOADED,
      conversationStep: ConversationStep.WELLBEING,
      bargeIn: false,
      failed: false,
      riskFlag: false,
      riskType: null,
      createdAt: now,
      completedAt: null,
      errorCode: null,
    };
    const completedUserTurn = {
      ...userTurn,
      status: ConversationTurnStatus.COMPLETED,
      completedAt: now,
    };
    const assistantTurn = {
      id: 'turn-assistant-1',
      callSessionId: 'session-1',
      clientTurnId: null,
      role: ConversationRole.ASSISTANT,
      text: '말씀을 잘 받았어요. 다음 단계에서 실제 AI 답변으로 연결할게요.',
      status: ConversationTurnStatus.COMPLETED,
      conversationStep: ConversationStep.WELLBEING,
      bargeIn: false,
      failed: false,
      riskFlag: false,
      riskType: null,
      createdAt: now,
      completedAt: now,
      errorCode: null,
    };

    prisma.callSession.findUnique.mockResolvedValue({
      ...baseSession,
      mode: CallSessionMode.AUTO_CONVERSATION,
      status: CallSessionStatus.WAITING_FOR_USER,
      currentStep: ConversationStep.WELLBEING,
    });
    prisma.conversationTurn.findFirst.mockResolvedValue(null);
    prisma.conversationTurn.create
      .mockResolvedValueOnce(userTurn)
      .mockResolvedValueOnce(assistantTurn);
    prisma.conversationTurn.update.mockResolvedValue(completedUserTurn);
    prisma.callSession.update.mockResolvedValue({});

    await expect(
      service.processAutoAudioTurn(
        'session-1',
        {
          clientTurnId: 'android-turn-1',
          mode: 'auto_conversation',
          conversationStep: 'wellbeing',
          bargeIn: 'false',
        },
        audio,
      ),
    ).resolves.toEqual({
      turnId: 'turn-user-1',
      clientTurnId: 'android-turn-1',
      sessionId: 'session-1',
      userText: '자동 발화가 접수됐어요.',
      assistantText:
        '말씀을 잘 받았어요. 다음 단계에서 실제 AI 답변으로 연결할게요.',
      audioMimeType: null,
      audioBase64: null,
      conversationStep: 'wellbeing',
      nextAction: 'play_audio',
      failed: false,
      riskFlag: false,
      riskType: null,
    });
    expect(prisma.conversationTurn.create).toHaveBeenCalledWith({
      data: {
        callSessionId: 'session-1',
        clientTurnId: 'android-turn-1',
        role: ConversationRole.USER,
        text: '자동 발화가 접수됐어요.',
        status: ConversationTurnStatus.UPLOADED,
        conversationStep: ConversationStep.WELLBEING,
        bargeIn: false,
      },
    });
    expect(prisma.callSession.update).toHaveBeenLastCalledWith({
      where: { id: 'session-1' },
      data: {
        status: CallSessionStatus.AI_SPEAKING,
        currentStep: ConversationStep.WELLBEING,
        turnCount: { increment: 1 },
      },
    });
  });

  it('returns a completed automatic turn for duplicate clientTurnId', async () => {
    const audio = {
      buffer: Buffer.from('audio'),
      mimetype: 'audio/mp4',
      originalname: 'auto-turn.m4a',
    } as Express.Multer.File;
    const existingTurn = {
      id: 'turn-user-1',
      callSessionId: 'session-1',
      clientTurnId: 'android-turn-1',
      role: ConversationRole.USER,
      text: '자동 발화가 접수됐어요.',
      status: ConversationTurnStatus.COMPLETED,
      conversationStep: ConversationStep.WELLBEING,
      bargeIn: false,
      failed: false,
      riskFlag: false,
      riskType: null,
      createdAt: now,
      completedAt: now,
      errorCode: null,
    };
    const assistantTurn = {
      ...existingTurn,
      id: 'turn-assistant-1',
      clientTurnId: null,
      role: ConversationRole.ASSISTANT,
      text: '이미 처리된 답변입니다.',
    };

    prisma.callSession.findUnique.mockResolvedValue({
      ...baseSession,
      mode: CallSessionMode.AUTO_CONVERSATION,
      status: CallSessionStatus.WAITING_FOR_USER,
    });
    prisma.conversationTurn.findFirst
      .mockResolvedValueOnce(existingTurn)
      .mockResolvedValueOnce(assistantTurn);

    await expect(
      service.processAutoAudioTurn(
        'session-1',
        { clientTurnId: 'android-turn-1', mode: 'auto_conversation' },
        audio,
      ),
    ).resolves.toEqual(
      expect.objectContaining({
        turnId: 'turn-user-1',
        clientTurnId: 'android-turn-1',
        assistantText: '이미 처리된 답변입니다.',
        nextAction: 'play_audio',
      }),
    );
    expect(prisma.conversationTurn.create).not.toHaveBeenCalled();
  });

  it('throws conflict when duplicate automatic turn is still processing', async () => {
    const audio = {
      buffer: Buffer.from('audio'),
      mimetype: 'audio/mp4',
      originalname: 'auto-turn.m4a',
    } as Express.Multer.File;

    prisma.callSession.findUnique.mockResolvedValue({
      ...baseSession,
      mode: CallSessionMode.AUTO_CONVERSATION,
      status: CallSessionStatus.WAITING_FOR_USER,
    });
    prisma.conversationTurn.findFirst.mockResolvedValue({
      id: 'turn-user-1',
      callSessionId: 'session-1',
      clientTurnId: 'android-turn-1',
      role: ConversationRole.USER,
      text: '자동 발화가 접수됐어요.',
      status: ConversationTurnStatus.TRANSCRIBING,
      conversationStep: null,
      bargeIn: false,
      failed: false,
      riskFlag: false,
      riskType: null,
      createdAt: now,
      completedAt: null,
      errorCode: null,
    });

    await expect(
      service.processAutoAudioTurn(
        'session-1',
        { clientTurnId: 'android-turn-1', mode: 'auto_conversation' },
        audio,
      ),
    ).rejects.toBeInstanceOf(ConflictException);
  });

  it('ends an active call session', async () => {
    prisma.callSession.findUnique.mockResolvedValue({
      id: 'session-1',
      status: CallSessionStatus.ACTIVE,
      startedAt: now,
      endedAt: null,
      expiresAt,
    });
    prisma.callSession.update.mockResolvedValue({
      id: 'session-1',
      status: CallSessionStatus.ENDED,
      startedAt: now,
      endedAt: now,
      expiresAt,
    });

    await expect(service.end('session-1')).resolves.toEqual({
      id: 'session-1',
      status: 'ENDED',
      mode: 'manual_recording',
      currentStep: null,
      turnCount: 0,
      targetTurnCount: 5,
      riskFlag: false,
      riskType: null,
      startedAt: '2026-05-16T05:00:00.000Z',
      endedAt: '2026-05-16T05:00:00.000Z',
      expiresAt: '2026-05-16T05:10:00.000Z',
    });
    expect(prisma.callSession.update).toHaveBeenCalledWith({
      where: { id: 'session-1' },
      data: {
        status: CallSessionStatus.ENDED,
        endedAt: now,
      },
    });
    expect(demoLogger.callSessionEnded).toHaveBeenCalledWith('session-1', now);
  });

  it('throws conflict when ending an already ended call session', async () => {
    prisma.callSession.findUnique.mockResolvedValue({
      id: 'session-1',
      status: CallSessionStatus.ENDED,
      startedAt: now,
      endedAt: now,
      expiresAt,
    });

    await expect(service.end('session-1')).rejects.toBeInstanceOf(
      ConflictException,
    );
  });
});
