import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';

type RiskLogContext = {
  riskFlag: boolean;
  riskType: string | null;
};

@Injectable()
export class DemoLoggerService {
  private readonly logger = new Logger('DemoLog');

  constructor(private readonly configService: ConfigService) {}

  callSessionCreated(callSessionId: string, expiresAt: Date): void {
    this.log(
      `call_session.created id=${callSessionId} expiresAt=${expiresAt.toISOString()}`,
    );
  }

  callSessionEnded(callSessionId: string, endedAt: Date | null): void {
    this.log(
      `call_session.ended id=${callSessionId} endedAt=${endedAt?.toISOString() ?? 'null'}`,
    );
  }

  voiceTurnStarted(
    callSessionId: string,
    filename: string,
    mimeType: string,
    size: number,
  ): void {
    this.log(
      `voice_turn.started id=${callSessionId} filename="${this.clean(filename)}" mimeType=${mimeType} size=${size}`,
    );
  }

  userTextTranscribed(
    callSessionId: string,
    text: string,
    risk: RiskLogContext,
  ): void {
    this.log(
      `voice_turn.user_text id=${callSessionId} riskFlag=${risk.riskFlag} riskType=${risk.riskType ?? 'none'}${this.formatText(text)}`,
    );
  }

  assistantTextGenerated(
    callSessionId: string,
    text: string,
    failed: boolean,
  ): void {
    this.log(
      `voice_turn.assistant_text id=${callSessionId} failed=${failed}${this.formatText(text)}`,
    );
  }

  assistantGenerationFailed(callSessionId: string, error: unknown): void {
    const message = error instanceof Error ? error.message : String(error);

    this.log(
      `voice_turn.assistant_failed id=${callSessionId} error="${this.clean(message)}"`,
    );
  }

  private log(message: string): void {
    if (!this.isEnabled('DEMO_LOG_ENABLED')) {
      return;
    }

    this.logger.log(message);
  }

  private formatText(text: string): string {
    if (this.isEnabled('DEMO_LOG_TEXT_ENABLED')) {
      return ` text="${this.clean(text)}"`;
    }

    return ` textLength=${text.length}`;
  }

  private isEnabled(key: string): boolean {
    const value = this.configService.get<string>(key, 'false').toLowerCase();

    return ['true', '1', 'yes', 'on'].includes(value);
  }

  private clean(value: string): string {
    return value.replace(/\s+/g, ' ').replace(/"/g, '\\"').trim().slice(0, 240);
  }
}
