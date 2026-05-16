import { Controller, Get } from '@nestjs/common';
import { AppService } from './app.service';
import type { HealthResponse } from './health-response';

@Controller()
export class AppController {
  constructor(private readonly appService: AppService) {}

  @Get('health')
  getHealth(): HealthResponse {
    return this.appService.getHealth();
  }
}
