import { Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { DemoLoggerService } from './demo-logger.service';

describe('DemoLoggerService', () => {
  let configService: {
    get: jest.Mock;
  };
  let service: DemoLoggerService;
  let logSpy: jest.SpyInstance;

  beforeEach(() => {
    configService = {
      get: jest.fn((_key: string, defaultValue: string) => defaultValue),
    };
    logSpy = jest.spyOn(Logger.prototype, 'log').mockImplementation();
    service = new DemoLoggerService(configService as unknown as ConfigService);
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('does not log when demo logs are disabled', () => {
    service.callSessionCreated(
      'session-1',
      new Date('2026-05-16T05:10:00.000Z'),
    );

    expect(logSpy).not.toHaveBeenCalled();
  });

  it('logs metadata without full text by default', () => {
    configService.get.mockImplementation((key: string, defaultValue: string) =>
      key === 'DEMO_LOG_ENABLED' ? 'true' : defaultValue,
    );

    service.userTextTranscribed('session-1', '안녕하세요.', {
      riskFlag: false,
      riskType: null,
    });

    expect(logSpy).toHaveBeenCalledWith(
      'voice_turn.user_text id=session-1 riskFlag=false riskType=none textLength=6',
    );
  });

  it('logs sanitized full text when text logs are enabled', () => {
    configService.get.mockImplementation((key: string, defaultValue: string) =>
      key === 'DEMO_LOG_ENABLED' || key === 'DEMO_LOG_TEXT_ENABLED'
        ? 'true'
        : defaultValue,
    );

    service.assistantTextGenerated('session-1', '안녕  "하세요"', false);

    expect(logSpy).toHaveBeenCalledWith(
      'voice_turn.assistant_text id=session-1 failed=false text="안녕 \\"하세요\\""',
    );
  });
});
