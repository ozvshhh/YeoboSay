import { Test, TestingModule } from '@nestjs/testing';
import { INestApplication } from '@nestjs/common';
import request from 'supertest';
import { App } from 'supertest/types';

jest.mock('./../src/prisma/prisma.service', () => ({
  PrismaService: jest.fn().mockImplementation(() => ({
    onModuleInit: jest.fn(),
    onModuleDestroy: jest.fn(),
  })),
}));

import { AppModule } from './../src/app.module';
import { PrismaService } from './../src/prisma/prisma.service';

describe('AppController (e2e)', () => {
  let app: INestApplication<App>;

  beforeEach(async () => {
    const moduleFixture: TestingModule = await Test.createTestingModule({
      imports: [AppModule],
    })
      .overrideProvider(PrismaService)
      .useValue({
        onModuleInit: jest.fn(),
        onModuleDestroy: jest.fn(),
      })
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

  afterEach(async () => {
    await app.close();
  });
});
