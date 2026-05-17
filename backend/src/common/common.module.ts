import { Module } from '@nestjs/common';
import { DemoLoggerService } from './demo-logger.service';

@Module({
  providers: [DemoLoggerService],
  exports: [DemoLoggerService],
})
export class CommonModule {}
