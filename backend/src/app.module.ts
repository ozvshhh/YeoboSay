import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { AppController } from './app.controller';
import { AppService } from './app.service';
import { CallInvitationsModule } from './call-invitations/call-invitations.module';
import { CallSessionsModule } from './call-sessions/call-sessions.module';
import { PrismaModule } from './prisma/prisma.module';

@Module({
  imports: [
    ConfigModule.forRoot({ isGlobal: true }),
    PrismaModule,
    CallSessionsModule,
    CallInvitationsModule,
  ],
  controllers: [AppController],
  providers: [AppService],
})
export class AppModule {}
