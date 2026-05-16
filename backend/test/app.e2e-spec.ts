import { Test, TestingModule } from '@nestjs/testing';
import { INestApplication } from '@nestjs/common';
import request from 'supertest';
import { App } from 'supertest/types';
import { AppModule } from './../src/app.module';
import { OpenAiService } from './../src/openai/openai.service';
import { PrismaService } from './../src/prisma/prisma.service';

describe('AppController (e2e)', () => {
  let app: INestApplication<App>;
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
    onModuleInit: jest.Mock;
    onModuleDestroy: jest.Mock;
  };
  let openAiService: {
    transcribeAudio: jest.Mock;
    generateAssistantText: jest.Mock;
    synthesizeSpeech: jest.Mock;
  };

  const startedAt = new Date('2026-05-16T05:00:00.000Z');
  const expiresAt = new Date('2026-05-16T05:10:00.000Z');

  beforeEach(async () => {
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
      onModuleInit: jest.fn(),
      onModuleDestroy: jest.fn(),
    };
    openAiService = {
      transcribeAudio: jest.fn(),
      generateAssistantText: jest.fn(),
      synthesizeSpeech: jest.fn(),
    };

    const moduleFixture: TestingModule = await Test.createTestingModule({
      imports: [AppModule],
    })
      .overrideProvider(PrismaService)
      .useValue(prisma)
      .overrideProvider(OpenAiService)
      .useValue(openAiService)
      .compile();

    app = moduleFixture.createNestApplication();
    await app.init();
  });

  it('/health (GET)', () => {
    return request(app.getHttpServer())
      .get('/health')
      .expect(200)
      .expect((response) => {
        const body = response.body as {
          ok: true;
          service: string;
          timestamp: string;
        };

        expect(body).toEqual({
          ok: true,
          service: 'YeoboSay Backend',
          timestamp: expect.any(String) as string,
        });
        expect(new Date(body.timestamp).toISOString()).toBe(body.timestamp);
      });
  });

  it('/call-sessions (POST)', () => {
    prisma.callSession.create.mockResolvedValue({
      id: 'session-1',
      status: 'ACTIVE',
      startedAt,
      endedAt: null,
      expiresAt,
    });

    return request(app.getHttpServer())
      .post('/call-sessions')
      .expect(201)
      .expect({
        id: 'session-1',
        status: 'ACTIVE',
        startedAt: '2026-05-16T05:00:00.000Z',
        endedAt: null,
        expiresAt: '2026-05-16T05:10:00.000Z',
      });
  });

  it('/call-sessions/:id (GET)', () => {
    prisma.callSession.findUnique.mockResolvedValue({
      id: 'session-1',
      status: 'ACTIVE',
      startedAt,
      endedAt: null,
      expiresAt,
    });

    return request(app.getHttpServer())
      .get('/call-sessions/session-1')
      .expect(200)
      .expect({
        id: 'session-1',
        status: 'ACTIVE',
        startedAt: '2026-05-16T05:00:00.000Z',
        endedAt: null,
        expiresAt: '2026-05-16T05:10:00.000Z',
      });
  });

  it('/call-sessions/:id/turns (GET)', () => {
    prisma.callSession.findUnique.mockResolvedValue({ id: 'session-1' });
    prisma.conversationTurn.findMany.mockResolvedValue([
      {
        id: 'turn-1',
        callSessionId: 'session-1',
        role: 'USER',
        text: '오늘 기분이 조금 외로워',
        failed: false,
        riskFlag: true,
        riskType: 'EMOTIONAL_DISTRESS',
        createdAt: startedAt,
      },
    ]);

    return request(app.getHttpServer())
      .get('/call-sessions/session-1/turns')
      .expect(200)
      .expect({
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
  });

  it('/call-sessions/:id/turns/audio (POST)', () => {
    prisma.callSession.findUnique.mockResolvedValue({
      id: 'session-1',
      status: 'ACTIVE',
      startedAt,
      endedAt: null,
      expiresAt: new Date(Date.now() + 10 * 60 * 1000),
    });
    openAiService.transcribeAudio.mockResolvedValue('오늘 산책을 했어요.');
    prisma.conversationTurn.create.mockResolvedValue({});
    prisma.conversationTurn.findMany.mockResolvedValue([
      {
        id: 'turn-1',
        callSessionId: 'session-1',
        role: 'USER',
        text: '오늘 산책을 했어요.',
        failed: false,
        riskFlag: false,
        riskType: null,
        createdAt: startedAt,
      },
    ]);
    openAiService.generateAssistantText.mockResolvedValue(
      '산책을 다녀오셨군요.',
    );
    openAiService.synthesizeSpeech.mockResolvedValue(Buffer.from('mp3'));

    return request(app.getHttpServer())
      .post('/call-sessions/session-1/turns/audio')
      .attach('audio', Buffer.from('audio'), {
        filename: 'turn.m4a',
        contentType: 'audio/mp4',
      })
      .expect(200)
      .expect({
        callSessionId: 'session-1',
        userText: '오늘 산책을 했어요.',
        assistantText: '산책을 다녀오셨군요.',
        audioMimeType: 'audio/mpeg',
        audioBase64: Buffer.from('mp3').toString('base64'),
        failed: false,
        riskFlag: false,
        riskType: null,
      });
  });

  it('/call-sessions/:id/end (POST)', () => {
    prisma.callSession.findUnique.mockResolvedValue({
      id: 'session-1',
      status: 'ACTIVE',
      startedAt,
      endedAt: null,
      expiresAt,
    });
    prisma.callSession.update.mockResolvedValue({
      id: 'session-1',
      status: 'ENDED',
      startedAt,
      endedAt: startedAt,
      expiresAt,
    });

    return request(app.getHttpServer())
      .post('/call-sessions/session-1/end')
      .expect(200)
      .expect({
        id: 'session-1',
        status: 'ENDED',
        startedAt: '2026-05-16T05:00:00.000Z',
        endedAt: '2026-05-16T05:00:00.000Z',
        expiresAt: '2026-05-16T05:10:00.000Z',
      });
  });

  it('/call-sessions/:id/end (POST) returns 409 for ended sessions', () => {
    prisma.callSession.findUnique.mockResolvedValue({
      id: 'session-1',
      status: 'ENDED',
      startedAt,
      endedAt: startedAt,
      expiresAt,
    });

    return request(app.getHttpServer())
      .post('/call-sessions/session-1/end')
      .expect(409);
  });

  afterEach(async () => {
    await app.close();
  });
});
