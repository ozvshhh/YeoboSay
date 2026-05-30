import { Logger } from '@nestjs/common';
import {
  OnGatewayConnection,
  OnGatewayDisconnect,
  WebSocketGateway,
  WebSocketServer,
} from '@nestjs/websockets';
import type { Server, Socket } from 'socket.io';
import { IncomingCallEventDto } from './incoming-call-event.dto';

@WebSocketGateway({
  namespace: 'call-invitations',
  cors: {
    origin: '*',
  },
})
export class CallInvitationsGateway
  implements OnGatewayConnection, OnGatewayDisconnect
{
  private readonly logger = new Logger(CallInvitationsGateway.name);

  @WebSocketServer()
  private server?: Server;

  handleConnection(client: Socket): void {
    this.logger.log(`Call invitation client connected: socketId=${client.id}`);
  }

  handleDisconnect(client: Socket): void {
    this.logger.log(
      `Call invitation client disconnected: socketId=${client.id}`,
    );
  }

  emitIncomingCall(event: IncomingCallEventDto): void {
    this.server?.emit('incoming_call', event);
    this.logger.log(
      `Incoming call event emitted: callInvitationId=${event.callInvitationId}`,
    );
  }
}
