import { Module } from '@nestjs/common';
import { CommonModule } from '../common/common.module';
import { OpenAiModule } from '../openai/openai.module';
import { PrismaModule } from '../prisma/prisma.module';
import { CallSessionsController } from './call-sessions.controller';
import { CallSessionsService } from './call-sessions.service';

@Module({
  imports: [PrismaModule, OpenAiModule, CommonModule],
  controllers: [CallSessionsController],
  providers: [CallSessionsService],
})
export class CallSessionsModule {}
