import { Injectable } from '@nestjs/common';
import { HealthResponse } from './health-response';

@Injectable()
export class AppService {
  getHealth(): HealthResponse {
    return {
      ok: true,
      service: 'YeoboSay Backend',
      timestamp: new Date().toISOString(),
    };
  }
}
