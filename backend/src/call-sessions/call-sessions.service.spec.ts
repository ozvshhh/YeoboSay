import {
  BadGatewayException,
  BadRequestException,
  ConflictException,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import { CallSessionStatus, ConversationRole } from '@prisma/client';
import { OpenAiService } from '../openai/openai.service';
import { PrismaService } from '../prisma/prisma.service';
import { CallSessionsService } from './call-sessions.service';

const now = new Date('2026-05-16T05:00:00.000Z');
const expiresAt = new Date('2026-05-16T05:10:00.000Z');

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
      findMany: jest.Mock;
    };
  };
  let openAiService: {
    transcribeAudio: jest.Mock;
    generateAssistantText: jest.Mock;
    synthesizeSpeech: jest.Mock;
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
        findMany: jest.fn(),
      },
    };
    openAiService = {
      transcribeAudio: jest.fn(),
      generateAssistantText: jest.fn(),
      synthesizeSpeech: jest.fn(),
    };

    service = new CallSessionsService(
      prisma as unknown as PrismaService,
      openAiService as unknown as OpenAiService,
    );
  });

  afterEach(() => {
    jest.restoreAllMocks();
    jest.useRealTimers();
  });

  it('creates an active call session with a 10 minute expiration', async () => {
    prisma.callSession.create.mockResolvedValue({
      id: 'session-1',
      status: CallSessionStatus.ACTIVE,
      startedAt: now,
      endedAt: null,
      expiresAt,
    });

    await expect(service.create()).resolves.toEqual({
      id: 'session-1',
      status: 'ACTIVE',
      startedAt: '2026-05-16T05:00:00.000Z',
      endedAt: null,
      expiresAt: '2026-05-16T05:10:00.000Z',
    });
    expect(prisma.callSession.create).toHaveBeenCalledWith({
      data: {
        startedAt: now,
        expiresAt,
      },
    });
  });

  it('returns an existing call session', async () => {
    prisma.callSession.findUnique.mockResolvedValue({
      id: 'session-1',
      status: CallSessionStatus.ACTIVE,
      startedAt: now,
      endedAt: null,
      expiresAt,
    });

    await expect(service.findOne('session-1')).resolves.toEqual({
      id: 'session-1',
      status: 'ACTIVE',
      startedAt: '2026-05-16T05:00:00.000Z',
      endedAt: null,
      expiresAt: '2026-05-16T05:10:00.000Z',
    });
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
