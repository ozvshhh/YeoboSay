import { Test, TestingModule } from '@nestjs/testing';
import { AppController } from './app.controller';
import { AppService } from './app.service';

describe('AppController', () => {
  let appController: AppController;

  beforeEach(async () => {
    const app: TestingModule = await Test.createTestingModule({
      controllers: [AppController],
      providers: [AppService],
    }).compile();

    appController = app.get<AppController>(AppController);
  });

  describe('health', () => {
    it('should return backend health status', () => {
      expect(appController.getHealth()).toEqual({
        ok: true,
        service: 'YeoboSay Backend',
        timestamp: expect.any(String) as string,
      });
    });
  });
});
